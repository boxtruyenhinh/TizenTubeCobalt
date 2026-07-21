// Copyright 2017 The Cobalt Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cobalt.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

/**
 * Transparent entry Activity for launcher, voice search and deep links.
 *
 * <p>This Activity has a separate task affinity so the CLEAR_TASK flag used
 * by legacy Google Search/Katniss 2.2 cannot destroy the running Cobalt task.
 */
public final class MainActivity extends Activity {

  private static final String TAG = "BoxTvVoiceProxy";

  public static final String EXTRA_LEGACY_KATNISS_22 =
      "dev.cobalt.app.extra.LEGACY_KATNISS_22";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    forwardToCobalt(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    forwardToCobalt(intent);
  }

  private void forwardToCobalt(Intent incomingIntent) {
    Intent sourceIntent =
        incomingIntent == null
            ? new Intent(Intent.ACTION_MAIN)
            : incomingIntent;

    boolean legacyKatniss22 =
        isLegacyKatniss22Intent(sourceIntent);

    /*
     * Copy action, URI, MIME type, categories, extras and ClipData,
     * but replace the explicit component and remove CLEAR_TASK.
     */
    Intent cobaltIntent =
        new Intent(sourceIntent);

    cobaltIntent.setClass(
        this,
        CobaltMainActivity.class);

    cobaltIntent.setFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TOP
            | Intent.FLAG_ACTIVITY_SINGLE_TOP);

    if (legacyKatniss22) {
      cobaltIntent.putExtra(
          EXTRA_LEGACY_KATNISS_22,
          true);
    }

    Log.i(
        TAG,
        "Forward action="
            + sourceIntent.getAction()
            + " data="
            + sourceIntent.getDataString()
            + " sourceFlags=0x"
            + Integer.toHexString(
                sourceIntent.getFlags())
            + " targetFlags=0x"
            + Integer.toHexString(
                cobaltIntent.getFlags())
            + " legacyKatniss22="
            + legacyKatniss22);

    try {
      startActivity(cobaltIntent);

      /*
       * Không dùng hiệu ứng chuyển Activity vì Activity này chỉ là
       * bộ định tuyến trong suốt.
       */
      overridePendingTransition(0, 0);
    } catch (RuntimeException error) {
      Log.e(
          TAG,
          "Cannot forward voice/deep-link intent",
          error);
    }

    finish();
  }

  private boolean isLegacyKatniss22Intent(
      Intent intent) {
    if (intent == null) {
      return false;
    }

    if (!Intent.ACTION_VIEW.equals(
        intent.getAction())) {
      return false;
    }

    if ((intent.getFlags()
            & Intent.FLAG_ACTIVITY_CLEAR_TASK)
        == 0) {
      return false;
    }

    Uri uri = intent.getData();

    if (uri == null) {
      return false;
    }

    if (!"http".equalsIgnoreCase(
        uri.getScheme())) {
      return false;
    }

    String host = uri.getHost();

    boolean youtubeHost =
        "youtube.com".equalsIgnoreCase(host)
            || "www.youtube.com".equalsIgnoreCase(host);

    if (!youtubeHost) {
      return false;
    }

    if (!"/watch".equals(uri.getPath())) {
      return false;
    }

    return !TextUtils.isEmpty(
        uri.getQueryParameter("v"));
  }
}
