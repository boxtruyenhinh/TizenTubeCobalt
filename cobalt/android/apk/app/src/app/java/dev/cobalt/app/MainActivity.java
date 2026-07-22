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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transparent entry Activity for launcher, voice search and deep links.
 *
 * <p>This Activity has a separate task affinity so the CLEAR_TASK flag used
 * by legacy Google Search/Katniss 2.2 cannot destroy the running Cobalt task.
 */
public final class MainActivity extends Activity {

  private static final String TAG =
      "BoxTvVoiceProxy";

  public static final String EXTRA_LEGACY_KATNISS_22 =
      "dev.cobalt.app.extra.LEGACY_KATNISS_22";

  public static final String EXTRA_RESOLVED_ASSISTANT =
      "dev.cobalt.app.extra.RESOLVED_ASSISTANT";

  private static final Pattern VIDEO_ID_PATTERN =
      Pattern.compile("^[A-Za-z0-9_-]{11}$");

  private static final Pattern VIDEO_PARAMETER_PATTERN =
      Pattern.compile(
          "(?i)(?:video[_-]?id|videoId|[?&]v)"
              + "[\"']?\\s*[:=]\\s*[\"']?"
              + "([A-Za-z0-9_-]{11})");

  private static final Pattern WATCH_URL_PATTERN =
      Pattern.compile(
          "(?i)(?:youtube\\.com/watch\\?[^\\s\"']*?[?&]?v=|youtu\\.be/)"
              + "([A-Za-z0-9_-]{11})");

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!RomCompatibilityGuard.isSupportedDevice()) {
      RomCompatibilityGuard.showUnsupportedScreen(this);
      return;
    }

    forwardToCobalt(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);

    if (!RomCompatibilityGuard.isSupportedDevice()) {
      RomCompatibilityGuard.showUnsupportedScreen(this);
      return;
    }

    forwardToCobalt(intent);
  }

  private void forwardToCobalt(
      Intent incomingIntent) {
    Intent sourceIntent =
        incomingIntent == null
            ? new Intent(Intent.ACTION_MAIN)
            : new Intent(incomingIntent);

    boolean legacyKatniss22 =
        isLegacyKatniss22Intent(sourceIntent);

    boolean assistantRootIntent =
        isAssistantRootIntent(sourceIntent);

    boolean assistantResolved = false;

    if (assistantRootIntent) {
      String assistantDeepLink =
          resolveAssistantDeepLink(sourceIntent);

      if (!TextUtils.isEmpty(assistantDeepLink)) {
        sourceIntent.setData(
            Uri.parse(assistantDeepLink));

        /*
         * Không để url_params tiếp tục thay đổi startup URL.
         * Video sẽ được mở bằng deep link đã chuẩn hóa.
         */
        sourceIntent.removeExtra("url_params");

        sourceIntent.putExtra(
            EXTRA_RESOLVED_ASSISTANT,
            true);

        assistantResolved = true;

        Log.i(
            TAG,
            "Resolved Assistant result to "
                + assistantDeepLink);
      } else {
        Log.w(
            TAG,
            "Assistant result has no recognized video id. Extras: "
                + describeExtraKeys(
                    sourceIntent.getExtras()));
      }
    }

    Intent cobaltIntent =
        new Intent(sourceIntent);

    cobaltIntent.setClass(
        this,
        CobaltMainActivity.class);

    /*
     * CobaltMainActivity đã dùng launchMode=singleTask.
     * Không truyền CLEAR_TASK hoặc CLEAR_TOP sang task Cobalt.
     */
    cobaltIntent.setFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_SINGLE_TOP);

    if (legacyKatniss22) {
      cobaltIntent.putExtra(
          EXTRA_LEGACY_KATNISS_22,
          true);
    }

    if (assistantResolved) {
      cobaltIntent.putExtra(
          EXTRA_RESOLVED_ASSISTANT,
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
                incomingIntent == null
                    ? 0
                    : incomingIntent.getFlags())
            + " targetFlags=0x"
            + Integer.toHexString(
                cobaltIntent.getFlags())
            + " legacyKatniss22="
            + legacyKatniss22
            + " assistantResolved="
            + assistantResolved);

    try {
      startActivity(cobaltIntent);
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
    if (intent == null
        || !Intent.ACTION_VIEW.equals(
            intent.getAction())) {
      return false;
    }

    if ((intent.getFlags()
            & Intent.FLAG_ACTIVITY_CLEAR_TASK)
        == 0) {
      return false;
    }

    Uri uri = intent.getData();

    if (uri == null
        || !"http".equalsIgnoreCase(
            uri.getScheme())
        || !isYouTubeHost(uri.getHost())
        || !"/watch".equals(uri.getPath())) {
      return false;
    }

    return !TextUtils.isEmpty(
        uri.getQueryParameter("v"));
  }

  private boolean isAssistantRootIntent(
      Intent intent) {
    if (intent == null
        || !Intent.ACTION_VIEW.equals(
            intent.getAction())) {
      return false;
    }

    /*
     * Katniss 2.2 đã có URL /watch rõ ràng và CLEAR_TASK,
     * không được đưa vào nhánh Assistant.
     */
    if ((intent.getFlags()
            & Intent.FLAG_ACTIVITY_CLEAR_TASK)
        != 0) {
      return false;
    }

    Uri uri = intent.getData();

    if (uri == null
        || !isYouTubeHost(uri.getHost())) {
      return false;
    }

    String path = uri.getPath();

    boolean rootPath =
        TextUtils.isEmpty(path)
            || "/".equals(path)
            || "/.".equals(path);

    return rootPath
        && TextUtils.isEmpty(
            uri.getQueryParameter("v"));
  }

  private String resolveAssistantDeepLink(
      Intent intent) {
    if (intent == null) {
      return "";
    }

    String videoId =
        findVideoId(
            intent.getExtras(),
            "extras",
            0);

    if (TextUtils.isEmpty(videoId)) {
      return "";
    }

    /*
     * Dùng HTTP vì đây là dạng URL đã được xác nhận hoạt động
     * ổn định với Cobalt và Katniss cũ.
     */
    return "http://www.youtube.com/watch?v="
        + videoId;
  }

  private String findVideoId(
      Object value,
      String keyHint,
      int depth) {
    if (value == null || depth > 5) {
      return "";
    }

    if (value instanceof Uri) {
      return findVideoIdInUri(
          (Uri) value);
    }

    if (value instanceof Intent) {
      Intent nestedIntent =
          (Intent) value;

      String fromUri =
          findVideoIdInUri(
              nestedIntent.getData());

      if (!TextUtils.isEmpty(fromUri)) {
        return fromUri;
      }

      return findVideoId(
          nestedIntent.getExtras(),
          keyHint,
          depth + 1);
    }

    if (value instanceof Bundle) {
      Bundle bundle = (Bundle) value;

      for (String key : bundle.keySet()) {
        Object nestedValue;

        try {
          nestedValue = bundle.get(key);
        } catch (RuntimeException error) {
          Log.w(
              TAG,
              "Cannot read Assistant extra key "
                  + key,
              error);
          continue;
        }

        String result =
            findVideoId(
                nestedValue,
                key,
                depth + 1);

        if (!TextUtils.isEmpty(result)) {
          Log.i(
              TAG,
              "Assistant video id found in extra key "
                  + key);
          return result;
        }
      }

      return "";
    }

    if (value instanceof Object[]) {
      Object[] values = (Object[]) value;

      for (Object item : values) {
        String result =
            findVideoId(
                item,
                keyHint,
                depth + 1);

        if (!TextUtils.isEmpty(result)) {
          return result;
        }
      }

      return "";
    }

    if (value instanceof Iterable<?>) {
      for (Object item : (Iterable<?>) value) {
        String result =
            findVideoId(
                item,
                keyHint,
                depth + 1);

        if (!TextUtils.isEmpty(result)) {
          return result;
        }
      }

      return "";
    }

    if (value instanceof CharSequence) {
      return findVideoIdInText(
          value.toString(),
          keyHint);
    }

    return "";
  }

  private String findVideoIdInUri(
      Uri uri) {
    if (uri == null) {
      return "";
    }

    String host = uri.getHost();

    if (TextUtils.isEmpty(host)) {
      return "";
    }

    if ("youtu.be".equalsIgnoreCase(host)) {
      String segment =
          uri.getLastPathSegment();

      return isValidVideoId(segment)
          ? segment
          : "";
    }

    if (!isYouTubeHost(host)) {
      return "";
    }

    String videoId;

    try {
      videoId =
          uri.getQueryParameter("v");
    } catch (RuntimeException error) {
      return "";
    }

    return isValidVideoId(videoId)
        ? videoId
        : "";
  }

  private String findVideoIdInText(
      String text,
      String keyHint) {
    if (TextUtils.isEmpty(text)) {
      return "";
    }

    String trimmed = text.trim();

    String fromUri =
        findVideoIdInUri(
            Uri.parse(trimmed));

    if (!TextUtils.isEmpty(fromUri)) {
      return fromUri;
    }

    String decoded =
        Uri.decode(trimmed);

    Matcher watchMatcher =
        WATCH_URL_PATTERN.matcher(decoded);

    if (watchMatcher.find()) {
      return watchMatcher.group(1);
    }

    Matcher parameterMatcher =
        VIDEO_PARAMETER_PATTERN.matcher(decoded);

    if (parameterMatcher.find()) {
      return parameterMatcher.group(1);
    }

    String normalizedKey =
        keyHint == null
            ? ""
            : keyHint.toLowerCase(Locale.US);

    boolean keyLooksLikeVideoId =
        "v".equals(normalizedKey)
            || normalizedKey.contains("video")
            || normalizedKey.contains("contentid")
            || normalizedKey.contains("content_id");

    if (keyLooksLikeVideoId
        && isValidVideoId(trimmed)) {
      return trimmed;
    }

    return "";
  }

  private boolean isValidVideoId(
      String value) {
    return !TextUtils.isEmpty(value)
        && VIDEO_ID_PATTERN
            .matcher(value)
            .matches();
  }

  private boolean isYouTubeHost(
      String host) {
    return "youtube.com".equalsIgnoreCase(host)
        || "www.youtube.com".equalsIgnoreCase(host)
        || "m.youtube.com".equalsIgnoreCase(host);
  }

  private String describeExtraKeys(
      Bundle extras) {
    if (extras == null || extras.isEmpty()) {
      return "none";
    }

    StringBuilder result =
        new StringBuilder();

    for (String key : extras.keySet()) {
      if (result.length() > 0) {
        result.append(", ");
      }

      result.append(key);

      try {
        Object value = extras.get(key);

        result.append("(")
            .append(
                value == null
                    ? "null"
                    : value.getClass()
                        .getSimpleName())
            .append(")");
      } catch (RuntimeException error) {
        result.append("(unreadable)");
      }
    }

    return result.toString();
  }
}
