// SPDX-License-Identifier: Apache-2.0
package com.android.settings.network.telephony;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserManager;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

import com.android.settings.R;

/** Native Settings confirmation and progress UI for Xiaomi's shared SIM slot. */
public final class SharedSimSlotDialogActivity extends SubscriptionActionDialogActivity
        implements ConfirmDialogFragment.OnConfirmListener {
    public static final String ACTION_SHARED_SIM_SLOT_DIALOG =
            "com.android.settings.action.SHARED_SIM_SLOT_DIALOG";
    public static final String EXTRA_CONTINUATION = "com.android.phone.euicc.extra.CONTINUATION";
    public static final String EXTRA_TARGET_STATE =
            "com.android.settings.extra.SHARED_SIM_TARGET_STATE";
    public static final String EXTRA_SLOT_PREPARED = "com.android.phone.euicc.extra.SLOT_PREPARED";

    private static final String TAG = "SharedSimSlotDialog";
    private static final String PHONE_PACKAGE = "com.android.phone";
    private static final ComponentName SERVICE =
            new ComponentName(PHONE_PACKAGE, "com.android.phone.euicc.SharedSimSlotService");
    private static final int MSG_READ = 1;
    private static final int MSG_SELECT = 2;
    private static final int SERVICE_RESULT_OK = 0;
    private static final int PHYSICAL = 0;
    private static final int EMBEDDED = 1;
    private static final int DIALOG_CONFIRM = 1;
    private static final String STATE_DIALOG_VISIBLE = "dialog_visible";
    private static final String STATE_FAILURE_VISIBLE = "failure_visible";

    private final Messenger mReceiver =
            new Messenger(new Handler(Looper.getMainLooper(), this::handleResponse));
    private Messenger mService;
    private boolean mBound;
    private boolean mActive;
    private boolean mDialogVisible;
    private boolean mFailureVisible;
    private int mTargetState;
    private Intent mContinuation;

    private final ServiceConnection mConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    mService = new Messenger(service);
                    if (mActive) sendRequest(MSG_READ, mTargetState);
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mService = null;
                    if (mActive && !isFinishing()) showFailure();
                }

                @Override
                public void onNullBinding(ComponentName name) {
                    mService = null;
                    if (mActive && !isFinishing()) showFailure();
                }

                @Override
                public void onBindingDied(ComponentName name) {
                    mService = null;
                    if (mActive && !isFinishing()) showFailure();
                }
            };

    public static Intent createIntent(Context context, boolean embedded, Intent continuation) {
        Intent intent =
                new Intent(context, SharedSimSlotDialogActivity.class)
                        .putExtra(EXTRA_TARGET_STATE, embedded ? EMBEDDED : PHYSICAL);
        if (continuation != null) intent.putExtra(EXTRA_CONTINUATION, continuation);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTargetState = getIntent().getIntExtra(EXTRA_TARGET_STATE, EMBEDDED);
        mContinuation = getIntent().getParcelableExtra(EXTRA_CONTINUATION, Intent.class);
        mDialogVisible =
                savedInstanceState != null && savedInstanceState.getBoolean(STATE_DIALOG_VISIBLE);
        mFailureVisible =
                savedInstanceState != null && savedInstanceState.getBoolean(STATE_FAILURE_VISIBLE);
        if ((mTargetState != PHYSICAL && mTargetState != EMBEDDED)
                || !EsimSlotSelection.INSTANCE.isSupported(this)
                || !isAdminAllowed()
                || !isValidContinuation(mContinuation)) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        if (!mDialogVisible) {
            showProgressDialog(getString(R.string.sim_action_enabling_sim_without_carrier_name));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_DIALOG_VISIBLE, mDialogVisible);
        outState.putBoolean(STATE_FAILURE_VISIBLE, mFailureVisible);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isFinishing()) return;
        mActive = true;
        Intent service = new Intent().setComponent(SERVICE);
        try {
            mBound = bindService(service, mConnection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException failure) {
            Log.e(TAG, "Unable to bind shared SIM service", failure);
        }
        if (!mBound) showFailure();
    }

    @Override
    protected void onStop() {
        mActive = false;
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        mService = null;
        super.onStop();
    }

    @Override
    public void onConfirm(int tag, boolean confirmed, int itemPosition) {
        mDialogVisible = false;
        if (tag != DIALOG_CONFIRM || !confirmed) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        String target =
                getString(
                        mTargetState == EMBEDDED
                                ? R.string.downloaded_sim_category_title
                                : R.string.sim_editor_title,
                        2);
        showProgressDialog(getString(R.string.sim_action_switch_sub_dialog_progress, target));
        sendRequest(MSG_SELECT, mTargetState);
    }

    private boolean handleResponse(Message response) {
        if (!mActive || isFinishing()) return true;
        if (response.arg2 != SERVICE_RESULT_OK) {
            showFailure();
            return true;
        }
        if (response.what == MSG_READ) {
            dismissProgressDialog();
            if (response.arg1 == mTargetState) {
                finishSuccess();
            } else if (!mDialogVisible) {
                mDialogVisible = true;
                ConfirmDialogFragment.show(
                        this,
                        ConfirmDialogFragment.OnConfirmListener.class,
                        DIALOG_CONFIRM,
                        getString(
                                mTargetState == EMBEDDED
                                        ? R.string.sim_action_switch_sub_dialog_title
                                        : R.string.sim_action_switch_psim_dialog_title,
                                getString(R.string.downloaded_sim_category_title)),
                        null,
                        getString(R.string.sim_switch_button),
                        getString(R.string.sim_action_cancel));
            }
        } else if (response.what == MSG_SELECT) {
            dismissProgressDialog();
            if (response.arg1 == mTargetState) finishSuccess();
            else showFailure();
        }
        return true;
    }

    private void sendRequest(int what, int state) {
        if (mService == null) {
            showFailure();
            return;
        }
        try {
            Message request = Message.obtain(null, what, state, 0);
            request.replyTo = mReceiver;
            mService.send(request);
        } catch (RemoteException failure) {
            Log.e(TAG, "Shared SIM service request failed", failure);
            showFailure();
        }
    }

    private void finishSuccess() {
        if (mContinuation != null) {
            Intent continuation = new Intent(mContinuation);
            continuation.setComponent(null);
            continuation.setPackage(PHONE_PACKAGE);
            continuation.setData(null);
            continuation.putExtra(EXTRA_SLOT_PREPARED, true);
            if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0) {
                continuation.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            }
            startActivity(continuation);
        }
        setResult(Activity.RESULT_OK);
        finish();
    }

    private void showFailure() {
        if (mFailureVisible || isFinishing()) return;
        mFailureVisible = true;
        dismissProgressDialog();
        showErrorDialog(
                getString(R.string.sim_action_enable_sim_fail_title),
                getString(R.string.sim_action_enable_sim_fail_text));
    }

    private boolean isAdminAllowed() {
        UserManager users = getSystemService(UserManager.class);
        return users != null
                && users.isAdminUser()
                && !users.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS);
    }

    private static boolean isValidContinuation(Intent continuation) {
        if (continuation == null) return true;
        String action = continuation.getAction();
        return continuation.getComponent() == null
                && continuation.getData() == null
                && PHONE_PACKAGE.equals(continuation.getPackage())
                && (EuiccManager.ACTION_PROVISION_EMBEDDED_SUBSCRIPTION.equals(action)
                        || EuiccManager.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS.equals(action));
    }
}
