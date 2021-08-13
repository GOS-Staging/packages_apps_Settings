/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.biometrics.face;

import android.app.admin.DevicePolicyManager;
import android.app.settings.SettingsEnums;
import android.content.Intent;
import android.hardware.biometrics.BiometricAuthenticator;
import android.hardware.face.FaceManager;
import android.hardware.face.FaceSensorPropertiesInternal;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.biometrics.BiometricEnrollIntroduction;
import com.android.settings.biometrics.BiometricUtils;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.password.ChooseLockSettingsHelper;
import com.android.settingslib.RestrictedLockUtilsInternal;

import com.google.android.setupcompat.template.FooterButton;
import com.google.android.setupcompat.util.WizardManagerHelper;
import com.google.android.setupdesign.span.LinkSpan;

import java.util.List;

/**
 * Provides introductory info about face unlock and prompts the user to agree before starting face
 * enrollment.
 */
public class FaceEnrollIntroduction extends BiometricEnrollIntroduction {
    private static final String TAG = "FaceEnrollIntroduction";

    private FaceManager mFaceManager;
    private FaceFeatureProvider mFaceFeatureProvider;
    @Nullable private FooterButton mPrimaryFooterButton;
    @Nullable private FooterButton mSecondaryFooterButton;

    @Override
    protected void onCancelButtonClick(View view) {
        if (!BiometricUtils.tryStartingNextBiometricEnroll(this, ENROLL_NEXT_BIOMETRIC_REQUEST)) {
            super.onCancelButtonClick(view);
        }
    }

    @Override
    protected void onSkipButtonClick(View view) {
        if (!BiometricUtils.tryStartingNextBiometricEnroll(this, ENROLL_NEXT_BIOMETRIC_REQUEST)) {
            super.onSkipButtonClick(view);
        }
    }

    @Override
    protected void onEnrollmentSkipped(@Nullable Intent data) {
        if (!BiometricUtils.tryStartingNextBiometricEnroll(this, ENROLL_NEXT_BIOMETRIC_REQUEST)) {
            super.onEnrollmentSkipped(data);
        }
    }

    @Override
    protected void onFinishedEnrolling(@Nullable Intent data) {
        if (!BiometricUtils.tryStartingNextBiometricEnroll(this, ENROLL_NEXT_BIOMETRIC_REQUEST)) {
            super.onFinishedEnrolling(data);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply extracted theme color to icons.
        final ImageView iconGlasses = findViewById(R.id.icon_glasses);
        final ImageView iconLooking = findViewById(R.id.icon_looking);
        iconGlasses.getBackground().setColorFilter(getIconColorFilter());
        iconLooking.getBackground().setColorFilter(getIconColorFilter());

        // Set text for views with multiple variations.
        final TextView infoMessageGlasses = findViewById(R.id.info_message_glasses);
        final TextView infoMessageLooking = findViewById(R.id.info_message_looking);
        final TextView howMessage = findViewById(R.id.how_message);
        final TextView inControlTitle = findViewById(R.id.title_in_control);
        final TextView inControlMessage = findViewById(R.id.message_in_control);
        infoMessageGlasses.setText(getInfoMessageGlasses());
        infoMessageLooking.setText(getInfoMessageLooking());
        inControlTitle.setText(getInControlTitle());
        howMessage.setText(getHowMessage());
        inControlMessage.setText(getInControlMessage());

        // Set up and show the "require eyes" info section if necessary.
        if (getResources().getBoolean(R.bool.config_face_intro_show_require_eyes)) {
            final LinearLayout infoRowRequireEyes = findViewById(R.id.info_row_require_eyes);
            final ImageView iconRequireEyes = findViewById(R.id.icon_require_eyes);
            final TextView infoMessageRequireEyes = findViewById(R.id.info_message_require_eyes);
            infoRowRequireEyes.setVisibility(View.VISIBLE);
            iconRequireEyes.getBackground().setColorFilter(getIconColorFilter());
            infoMessageRequireEyes.setText(getInfoMessageRequireEyes());
        }

        mFaceManager = Utils.getFaceManagerOrNull(this);
        mFaceFeatureProvider = FeatureFactory.getFactory(getApplicationContext())
                .getFaceFeatureProvider();

        // This path is an entry point for SetNewPasswordController, e.g.
        // adb shell am start -a android.app.action.SET_NEW_PASSWORD
        if (mToken == null && BiometricUtils.containsGatekeeperPasswordHandle(getIntent())) {
            if (generateChallengeOnCreate()) {
                mFooterBarMixin.getPrimaryButton().setEnabled(false);
                // We either block on generateChallenge, or need to gray out the "next" button until
                // the challenge is ready. Let's just do this for now.
                mFaceManager.generateChallenge(mUserId, (sensorId, userId, challenge) -> {
                    mToken = BiometricUtils.requestGatekeeperHat(this, getIntent(), mUserId,
                            challenge);
                    mSensorId = sensorId;
                    mChallenge = challenge;
                    mFooterBarMixin.getPrimaryButton().setEnabled(true);
                });
            }
        }
    }

    protected boolean generateChallengeOnCreate() {
        return true;
    }

    @StringRes
    protected int getInfoMessageGlasses() {
        return R.string.security_settings_face_enroll_introduction_info_glasses;
    }

    @StringRes
    protected int getInfoMessageLooking() {
        return R.string.security_settings_face_enroll_introduction_info_looking;
    }

    @StringRes
    protected int getInfoMessageRequireEyes() {
        return R.string.security_settings_face_enroll_introduction_info_gaze;
    }

    @StringRes
    protected int getHowMessage() {
        return R.string.security_settings_face_enroll_introduction_how_message;
    }

    @StringRes
    protected int getInControlTitle() {
        return R.string.security_settings_face_enroll_introduction_control_title;
    }

    @StringRes
    protected int getInControlMessage() {
        return R.string.security_settings_face_enroll_introduction_control_message;
    }

    @Override
    protected boolean isDisabledByAdmin() {
        return RestrictedLockUtilsInternal.checkIfKeyguardFeaturesDisabled(
                this, DevicePolicyManager.KEYGUARD_DISABLE_FACE, mUserId) != null;
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.face_enroll_introduction;
    }

    @Override
    protected int getHeaderResDisabledByAdmin() {
        return R.string.security_settings_face_enroll_introduction_title_unlock_disabled;
    }

    @Override
    protected int getHeaderResDefault() {
        return R.string.security_settings_face_enroll_introduction_title;
    }

    @Override
    protected int getDescriptionResDisabledByAdmin() {
        return R.string.security_settings_face_enroll_introduction_message_unlock_disabled;
    }

    @Override
    protected FooterButton getCancelButton() {
        if (mFooterBarMixin != null) {
            return mFooterBarMixin.getSecondaryButton();
        }
        return null;
    }

    @Override
    protected FooterButton getNextButton() {
        if (mFooterBarMixin != null) {
            return mFooterBarMixin.getPrimaryButton();
        }
        return null;
    }

    @Override
    protected TextView getErrorTextView() {
        return findViewById(R.id.error_text);
    }

    private boolean maxFacesEnrolled() {
        if (mFaceManager != null) {
            final List<FaceSensorPropertiesInternal> props =
                    mFaceManager.getSensorPropertiesInternal();
            // This will need to be updated for devices with multiple face sensors.
            final int max = props.get(0).maxEnrollmentsPerUser;
            final int numEnrolledFaces = mFaceManager.getEnrolledFaces(mUserId).size();
            return numEnrolledFaces >= max;
        } else {
            return false;
        }
    }

    //TODO: Refactor this to something that conveys it is used for getting a string ID.
    @Override
    protected int checkMaxEnrolled() {
        if (mFaceManager != null) {
            if (maxFacesEnrolled()) {
                return R.string.face_intro_error_max;
            }
        } else {
            return R.string.face_intro_error_unknown;
        }
        return 0;
    }

    @Override
    protected void getChallenge(GenerateChallengeCallback callback) {
        mFaceManager = Utils.getFaceManagerOrNull(this);
        if (mFaceManager == null) {
            callback.onChallengeGenerated(0, 0, 0L);
            return;
        }
        mFaceManager.generateChallenge(mUserId, callback::onChallengeGenerated);
    }

    @Override
    protected String getExtraKeyForBiometric() {
        return ChooseLockSettingsHelper.EXTRA_KEY_FOR_FACE;
    }

    @Override
    protected Intent getEnrollingIntent() {
        Intent intent = new Intent(this, FaceEnrollEducation.class);
        WizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
        return intent;
    }

    @Override
    protected int getConfirmLockTitleResId() {
        return R.string.security_settings_face_preference_title;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.FACE_ENROLL_INTRO;
    }

    @Override
    public void onClick(LinkSpan span) {
        // TODO(b/110906762)
    }

    @Override
    public @BiometricAuthenticator.Modality int getModality() {
        return BiometricAuthenticator.TYPE_FACE;
    }

    @Override
    @NonNull
    protected FooterButton getPrimaryFooterButton() {
        if (mPrimaryFooterButton == null) {
            mPrimaryFooterButton = new FooterButton.Builder(this)
                    .setText(R.string.security_settings_face_enroll_introduction_agree)
                    .setButtonType(FooterButton.ButtonType.OPT_IN)
                    .setListener(this::onNextButtonClick)
                    .setTheme(R.style.SudGlifButton_Primary)
                    .build();
        }
        return mPrimaryFooterButton;
    }

    @Override
    @NonNull
    protected FooterButton getSecondaryFooterButton() {
        if (mSecondaryFooterButton == null) {
            mSecondaryFooterButton = new FooterButton.Builder(this)
                    .setText(R.string.security_settings_face_enroll_introduction_no_thanks)
                    .setListener(this::onSkipButtonClick)
                    .setButtonType(FooterButton.ButtonType.NEXT)
                    .setTheme(R.style.SudGlifButton_Primary)
                    .build();
        }
        return mSecondaryFooterButton;
    }

    @Override
    @StringRes
    protected int getAgreeButtonTextRes() {
        return R.string.security_settings_fingerprint_enroll_introduction_agree;
    }

    @Override
    @StringRes
    protected int getMoreButtonTextRes() {
        return R.string.security_settings_face_enroll_introduction_more;
    }
}
