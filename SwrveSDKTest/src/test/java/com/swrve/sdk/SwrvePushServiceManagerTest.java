package com.swrve.sdk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;

import com.swrve.sdk.config.SwrveConfig;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static com.swrve.sdk.ISwrveCommon.EVENT_TYPE_GENERIC_CAMPAIGN;
import static com.swrve.sdk.ISwrveCommon.GENERIC_EVENT_ACTION_TYPE_INFLUENCED;
import static com.swrve.sdk.ISwrveCommon.GENERIC_EVENT_ACTION_TYPE_KEY;
import static com.swrve.sdk.ISwrveCommon.GENERIC_EVENT_CAMPAIGN_TYPE_KEY;
import static com.swrve.sdk.ISwrveCommon.GENERIC_EVENT_CAMPAIGN_TYPE_PUSH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class SwrvePushServiceManagerTest extends SwrveBaseTest {

    private int dummyIconResource = 12345;
    private NotificationChannel dummyChannel = null;

    @Test
    public void testNotificationConfigAccentColorHex() {

        String colorHexMocked = "#217913";
        SwrveNotificationConfig.Builder notificationConfig = new SwrveNotificationConfig.Builder(dummyIconResource, dummyIconResource, dummyChannel)
                .accentColorHex(colorHexMocked);
        SwrveConfig config = new SwrveConfig();
        config.setNotificationConfig(notificationConfig.build());
        SwrveSDK.createInstance(RuntimeEnvironment.application, 1, "apiKey", config);

        assertNumberOfNotification(0); // 0 to begin with because nothing has been processed
        sendSimpleBundleToPushServiceManager();
        assertNumberOfNotification(1); // there can only be one notification for this test

        NotificationManager notificationManager = (NotificationManager) RuntimeEnvironment.application.getSystemService(Context.NOTIFICATION_SERVICE);
        List<Notification> notifications = shadowOf(notificationManager).getAllNotifications();
        assertEquals(Color.parseColor(colorHexMocked), notifications.get(0).color);
    }

    @Test
    public void testNotificationConfigAccentColorInvalidHex() {

        SwrveNotificationConfig.Builder notificationConfig = new SwrveNotificationConfig.Builder(dummyIconResource, dummyIconResource, dummyChannel).accentColorHex("SomeInvalidHeColor123");;
        SwrveConfig config = new SwrveConfig();
        config.setNotificationConfig(notificationConfig.build());
        SwrveSDK.createInstance(RuntimeEnvironment.application, 1, "apiKey", config);

        assertNumberOfNotification(0); // 0 to begin with because nothing has been processed
        sendSimpleBundleToPushServiceManager();
        assertNumberOfNotification(1); // there can only be one notification for this test

        NotificationManager notificationManager = (NotificationManager) RuntimeEnvironment.application.getSystemService(Context.NOTIFICATION_SERVICE);
        List<Notification> notifications = shadowOf(notificationManager).getAllNotifications();
        assertEquals(0, notifications.get(0).color); // if we don't set color the default there is 0;
    }

    @Test
    public void testNotificationFilterSuppress() {

        // create pointer to customFilter and change it throughout the test.
        SwrveNotificationFilter notificationFilter = null; // default is null
        SwrveNotificationConfig.Builder notificationConfig = new SwrveNotificationConfig.Builder(dummyIconResource, dummyIconResource, dummyChannel).notificationFilter(notificationFilter);

        SwrveConfig config = new SwrveConfig();
        config.setNotificationConfig(notificationConfig.build());
        SwrveSDK.createInstance(RuntimeEnvironment.application, 1, "apiKey", config);

        assertNumberOfNotification(0); // 0 to begin with because nothing has been processed

        sendSimpleBundleToPushServiceManager();
        assertNumberOfNotification(1); // +1 because customFilter is null and default implementation is to return same notification

        notificationFilter = (builder, id, notificationDetails, jsonPayload) -> null; // returning null here will suppress it
        notificationConfig = new SwrveNotificationConfig.Builder(dummyIconResource, dummyIconResource, dummyChannel).notificationFilter(notificationFilter);
        SwrveSDK.getConfig().setNotificationConfig(notificationConfig.build());
        sendSimpleBundleToPushServiceManager();
        assertNumberOfNotification(1); // still 1 because new customFilter has suppressed it

        notificationFilter = (builder, id, notificationDetails, jsonPayload) -> builder.build();
        notificationConfig = new SwrveNotificationConfig.Builder(dummyIconResource, dummyIconResource, dummyChannel).notificationFilter(notificationFilter);
        SwrveSDK.getConfig().setNotificationConfig(notificationConfig.build());
        sendSimpleBundleToPushServiceManager();
        assertNumberOfNotification(2); // +1, so total is 2 because new customFilter
    }

    @Test
    public void testNotificationFilterModify() {

        // create pointer to customFilter and change it throughout the test.
        SwrveNotificationFilter notificationFilter = null; // default is null
        SwrveNotificationConfig.Builder notificationConfig = new SwrveNotificationConfig.Builder(dummyIconResource, dummyIconResource, dummyChannel).notificationFilter(notificationFilter);

        SwrveConfig config = new SwrveConfig();
        config.setNotificationConfig(notificationConfig.build());
        SwrveSDK.createInstance(RuntimeEnvironment.application, 1, "apiKey", config);

        assertNumberOfNotification(0); // 0 to begin with because nothing has been processed

        sendSimpleBundleToPushServiceManager();
        assertTickerText("plain text");

        // clear all and test modifying a notification
        NotificationManager notificationManager = (NotificationManager) RuntimeEnvironment.application.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
        assertNumberOfNotification(0);

        notificationFilter = (builder, id, notificationDetails, jsonPayload) -> builder.setTicker("modified ticker text").build();
        notificationConfig = new SwrveNotificationConfig.Builder(dummyIconResource, dummyIconResource, dummyChannel).notificationFilter(notificationFilter);
        SwrveSDK.getConfig().setNotificationConfig(notificationConfig.build());
        sendSimpleBundleToPushServiceManager();
        assertTickerText("modified ticker text");
    }

    @Test
    public void testSimpleNotificationFilterDetails() {

        SwrveNotificationFilter notificationFilter = (builder, id, notificationDetails, jsonPayload) -> {
            assertEquals("title", notificationDetails.getTitle());
            assertEquals("expanded title", notificationDetails.getExpandedTitle());
            assertEquals("plain text", notificationDetails.getBody());
            assertEquals("expanded body", notificationDetails.getExpandedBody());
            assertNull(notificationDetails.getMediaUrl());
            assertNull(notificationDetails.getMediaBitmap());
            return builder.setTicker("modified ticker text").build();
        };

        SwrveNotificationConfig notificationConfig = new SwrveNotificationConfig.Builder(dummyIconResource, dummyIconResource, dummyChannel).notificationFilter(notificationFilter).build();
        SwrveConfig config = new SwrveConfig();
        config.setNotificationConfig(notificationConfig);
        SwrveSDK.createInstance(RuntimeEnvironment.application, 1, "apiKey", config);

        sendSimpleBundleToPushServiceManager();

        assertTickerText("modified ticker text");
    }

    @Test
    public void testRichNotificationFilterDetails() {

        SwrveNotificationFilter notificationFilter = (builder, id, notificationDetails, jsonPayload) -> {
            assertEquals("title", notificationDetails.getTitle());
            assertEquals("expanded title", notificationDetails.getExpandedTitle());
            assertEquals("body", notificationDetails.getBody());
            assertEquals("expanded body", notificationDetails.getExpandedBody());
            assertEquals("https://media.jpg", notificationDetails.getMediaUrl());
            assertNotNull(notificationDetails.getMediaBitmap());
            return builder.setTicker("modified ticker text").build();
        };

        SwrveNotificationConfig notificationConfig = new SwrveNotificationConfig.Builder(dummyIconResource, dummyIconResource, dummyChannel).notificationFilter(notificationFilter).build();
        SwrveConfig config = new SwrveConfig();
        config.setNotificationConfig(notificationConfig);
        SwrveSDK.createInstance(RuntimeEnvironment.application, 1, "apiKey", config);

        sendRichBundleToPushServiceManager(notificationConfig);

        assertTickerText("modified ticker text");
    }

    @Test
    public void testNotificationFilterJsonPayload() {

        SwrveNotificationFilter notificationFilterSpy = spy(new MySwrveNotificationFilter());
        SwrveNotificationConfig.Builder notificationConfig = new SwrveNotificationConfig.Builder(dummyIconResource, dummyIconResource, dummyChannel).notificationFilter(notificationFilterSpy);
        SwrveConfig config = new SwrveConfig();
        config.setNotificationConfig(notificationConfig.build());
        SwrveSDK.createInstance(RuntimeEnvironment.application, 1, "apiKey", config);

        assertNumberOfNotification(0); // 0 to begin with because nothing has been processed

        sendSimpleBundleToPushServiceManager();

        // verify the custom payload is delivered
        verify(notificationFilterSpy, atLeastOnce())
                .filterNotification(any(NotificationCompat.Builder.class), anyInt(), any(SwrveNotificationDetails.class), eq("{\"customKey\":\"customValues\"}"));
    }

    @Test
    public void testNotificationAuthenticatedSavedToDB() throws Exception {

        SwrveNotificationConfig.Builder notificationConfig = new SwrveNotificationConfig.Builder(dummyIconResource, dummyIconResource, dummyChannel);
        SwrveConfig config = new SwrveConfig();
        config.setNotificationConfig(notificationConfig.build());
        Swrve swrveReal = (Swrve) SwrveSDK.createInstance(RuntimeEnvironment.application, 1, "apiKey", config);
        Swrve swrveSpy = Mockito.spy(swrveReal);
        SwrveTestUtils.setSDKInstance(swrveSpy);
        SwrveCommon.setSwrveCommon(swrveSpy);

        assertNumberOfNotification(0);

        Bundle bundleWithAUI = new Bundle();
        bundleWithAUI.putString(SwrveNotificationConstants.SWRVE_AUTH_USER_KEY, SwrveSDK.getUserId());
        bundleWithAUI.putString(SwrveNotificationConstants.SWRVE_TRACKING_KEY, "1");
        String json = "{\n" +
                "\"notification_id\": 123,\n" +
                "\"title\": \"title\",\n" +
                "\"subtitle\": \"subtitle\",\n" +
                "\"version\": 1,\n" +
                "\"expanded\": {" +
                "\"title\": \"expanded title\",\n" +
                "\"body\": \"expanded body\"}" +
                "}";
        bundleWithAUI.putString(SwrveNotificationConstants.SWRVE_PAYLOAD_KEY, json);
        bundleWithAUI.putString(SwrveNotificationConstants.TEXT_KEY, "plain text");
        bundleWithAUI.putString(SwrveNotificationConstants.SWRVE_NESTED_JSON_PAYLOAD_KEY, "{ 'customKey': 'customValues' }");
        bundleWithAUI.putString("sound", "default");

        SwrvePushServiceManager pushServiceManager = new SwrvePushServiceManager(mActivity);
        pushServiceManager.processMessage(bundleWithAUI);

        assertNumberOfNotification(1);

        Mockito.verify(swrveSpy).saveNotificationAuthenticated(123);
    }

    @Test
    public void testNonNotificationAuthenticatedNotSavedToDB() throws Exception {

        SwrveNotificationConfig.Builder notificationConfig = new SwrveNotificationConfig.Builder(dummyIconResource, dummyIconResource, dummyChannel);
        SwrveConfig config = new SwrveConfig();
        config.setNotificationConfig(notificationConfig.build());
        Swrve swrveReal = (Swrve) SwrveSDK.createInstance(RuntimeEnvironment.application, 1, "apiKey", config);
        Swrve swrveSpy = Mockito.spy(swrveReal);
        SwrveTestUtils.setSDKInstance(swrveSpy);
        SwrveCommon.setSwrveCommon(swrveSpy);

        assertNumberOfNotification(0);

        Bundle bundleWithoutAUI = new Bundle();
        bundleWithoutAUI.putString(SwrveNotificationConstants.SWRVE_TRACKING_KEY, "1");
        String json = "{\n" +
                "\"notification_id\": 123,\n" +
                "\"title\": \"title\",\n" +
                "\"subtitle\": \"subtitle\",\n" +
                "\"version\": 1,\n" +
                "\"expanded\": {" +
                "\"title\": \"expanded title\",\n" +
                "\"body\": \"expanded body\"}" +
                "}";
        bundleWithoutAUI.putString(SwrveNotificationConstants.SWRVE_PAYLOAD_KEY, json);
        bundleWithoutAUI.putString(SwrveNotificationConstants.TEXT_KEY, "plain text");
        bundleWithoutAUI.putString(SwrveNotificationConstants.SWRVE_NESTED_JSON_PAYLOAD_KEY, "{ 'customKey': 'customValues' }");
        bundleWithoutAUI.putString("sound", "default");

        SwrvePushServiceManager pushServiceManager = new SwrvePushServiceManager(mActivity);
        pushServiceManager.processMessage(bundleWithoutAUI);

        assertNumberOfNotification(1);

        Mockito.verify(swrveSpy, never()).saveNotificationAuthenticated(anyInt());
    }

    @Test
    public void testSilentPush() throws Exception {

        Swrve swrveReal = (Swrve) SwrveSDK.createInstance(RuntimeEnvironment.application, 1, "apiKey");
        Swrve swrveSpy = spy(swrveReal);
        SwrveTestUtils.disableBeforeSendDeviceInfo(swrveReal, swrveSpy); // disable token registration

        SwrveSilentPushListener silentPushListenerMock = Mockito.mock(SwrveSilentPushListener.class);
        ISwrveCommon swrveCommonSpy = mock(ISwrveCommon.class);
        SwrveCommon.setSwrveCommon(swrveCommonSpy);
        doReturn(silentPushListenerMock).when(swrveCommonSpy).getSilentPushListener();

        SwrvePushServiceManager pushServiceManager = new SwrvePushServiceManager(mActivity);
        SwrvePushServiceManager pushServiceManagerSpy = spy(pushServiceManager);

        // Send some valid silent pushes
        Bundle bundle = new Bundle();
        bundle.putString(SwrveNotificationConstants.SWRVE_SILENT_TRACKING_KEY, "1");
        bundle.putString(SwrveNotificationConstants.SWRVE_INFLUENCED_WINDOW_MINS_KEY, "720");
        bundle.putString(SwrveNotificationConstants.SILENT_PAYLOAD_KEY, "{\"custom\":\"value1\"}");
        doReturn(SwrveTestUtils.parseDate("2017/01/01 0:00")).when(pushServiceManagerSpy).getNow();
        pushServiceManagerSpy.processMessage(bundle);

        ArgumentCaptor<JSONObject> payloadCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(silentPushListenerMock, atLeastOnce()).onSilentPush(any(Context.class), payloadCaptor.capture());
        assertEquals("{\"custom\":\"value1\"}", payloadCaptor.getValue().toString());

        // Silent push 2
        bundle.putString(SwrveNotificationConstants.SWRVE_SILENT_TRACKING_KEY, "2");
        bundle.putString(SwrveNotificationConstants.SWRVE_INFLUENCED_WINDOW_MINS_KEY, "720");
        bundle.putString(SwrveNotificationConstants.SILENT_PAYLOAD_KEY, "{\"custom\":\"value2\"}");
        doReturn(SwrveTestUtils.parseDate("2017/01/01 10:00")).when(pushServiceManagerSpy).getNow();
        pushServiceManagerSpy.processMessage(bundle);


        // Silent push 3
        bundle.putString(SwrveNotificationConstants.SWRVE_SILENT_TRACKING_KEY, "3");
        bundle.putString(SwrveNotificationConstants.SWRVE_INFLUENCED_WINDOW_MINS_KEY, "720");
        bundle.putString(SwrveNotificationConstants.SILENT_PAYLOAD_KEY, "{\"custom\":\"value3\"}");
        doReturn(SwrveTestUtils.parseDate("2017/01/01 11:00")).when(pushServiceManagerSpy).getNow();
        pushServiceManagerSpy.processMessage(bundle);

        assertNumberOfNotification(0);

        // Init the SDK, should read influence data
        swrveSpy.campaignInfluence = spy(new SwrveCampaignInfluence());
        when(swrveSpy.campaignInfluence.getNow()).thenReturn(SwrveTestUtils.parseDate("2017/01/01 13:00"));
        swrveSpy.init(mActivity);
        swrveSpy.onPause();
        swrveSpy.onResume(mActivity);
        swrveSpy.onPause();
        swrveSpy.onResume(mActivity);

        List<Intent> eventIntents = shadowApplication.getBroadcastIntents();
        assertEquals(1, eventIntents.size());
        Intent eventIntent = eventIntents.get(0);
        ArrayList extras = (ArrayList) eventIntent.getExtras().get("swrve_wakeful_events");
        assertEquals(2, extras.size());
        JSONObject event1 = new JSONObject((String) extras.get(0));
        assertEquals(EVENT_TYPE_GENERIC_CAMPAIGN, event1.get("type"));
        assertEquals(2, event1.get("id"));
        assertEquals(GENERIC_EVENT_CAMPAIGN_TYPE_PUSH, event1.get(GENERIC_EVENT_CAMPAIGN_TYPE_KEY));
        assertEquals(GENERIC_EVENT_ACTION_TYPE_INFLUENCED, event1.get(GENERIC_EVENT_ACTION_TYPE_KEY));
        JSONObject payload1 = event1.getJSONObject("payload");
        assertEquals("540", payload1.get("delta"));

        JSONObject event2 = new JSONObject((String) extras.get(1));
        assertEquals(3, event2.get("id"));
    }

    private void assertNumberOfNotification(int numberOfNotifications) {
        NotificationManager notificationManager = (NotificationManager) RuntimeEnvironment.application.getSystemService(Context.NOTIFICATION_SERVICE);
        List<Notification> notifications = shadowOf(notificationManager).getAllNotifications();
        assertEquals(numberOfNotifications, notifications.size());
    }

    private void assertTickerText(String tickerText) {
        assertNumberOfNotification(1); // there can only be one notification for this test
        NotificationManager notificationManager = (NotificationManager) RuntimeEnvironment.application.getSystemService(Context.NOTIFICATION_SERVICE);
        List<Notification> notifications = shadowOf(notificationManager).getAllNotifications();
        assertEquals(tickerText, notifications.get(0).tickerText);
    }

    private void sendSimpleBundleToPushServiceManager() {
        Bundle bundle = new Bundle();
        bundle.putString(SwrveNotificationConstants.SWRVE_TRACKING_KEY, "1");
        String json = "{\n" +
                "\"title\": \"title\",\n" +
                "\"subtitle\": \"subtitle\",\n" +
                "\"version\": 1,\n" +
                "\"expanded\": {" +
                "\"title\": \"expanded title\",\n" +
                "\"body\": \"expanded body\"}" +
                "}";
        bundle.putString(SwrveNotificationConstants.SWRVE_PAYLOAD_KEY, json);
        bundle.putString(SwrveNotificationConstants.TEXT_KEY, "plain text");
        bundle.putString(SwrveNotificationConstants.SWRVE_NESTED_JSON_PAYLOAD_KEY, "{ 'customKey': 'customValues' }");
        bundle.putString("sound", "default");

        SwrvePushServiceManager pushServiceManager = new SwrvePushServiceManager(mActivity);
        pushServiceManager.processMessage(bundle);
    }

    private void sendRichBundleToPushServiceManager(SwrveNotificationConfig notificationConfig) {
        Bundle bundle = new Bundle();
        bundle.putString(SwrveNotificationConstants.SWRVE_TRACKING_KEY, "1");
        String json = "{\n" +
                "\"title\": \"title\",\n" +
                "\"subtitle\": \"subtitle\",\n" +
                "\"version\": 1,\n" +
                "\"expanded\": {" +
                    "\"title\": \"expanded title\",\n" +
                    "\"body\": \"expanded body\"}," +
                " \"media\": { \"title\": \"title\",\n" +
                    "\"subtitle\": \"subtitle\",\n" +
                    "\"body\": \"body\",\n" +
                    "\"type\": \"image\",\n" +
                    "\"url\": \"https://media.jpg\",\n" +
                    "\"fallback_type\": \"image\",\n" +
                    "\"fallback_url\": \"https://valid-image.png\",\n" +
                    "\"fallback_sd\": \"https://video.com\"\n }" +
                "}";
        bundle.putString(SwrveNotificationConstants.SWRVE_PAYLOAD_KEY, json);
        bundle.putString(SwrveNotificationConstants.TEXT_KEY, "plain text");
        bundle.putString(SwrveNotificationConstants.SWRVE_NESTED_JSON_PAYLOAD_KEY, "{ 'customKey': 'customValues' }");
        bundle.putString("sound", "default");

        SwrvePushServiceManager pushServiceManagerSpy = Mockito.spy(new SwrvePushServiceManager(mActivity));
        SwrveNotificationBuilder notificationBuilderSpy = Mockito.spy(new SwrveNotificationBuilder(mActivity, notificationConfig));
        Mockito.doReturn(notificationBuilderSpy).when(pushServiceManagerSpy).getSwrveNotificationBuilder();
        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        Bitmap bmp = Bitmap.createBitmap(200, 300, conf);
        Mockito.doReturn(bmp).when(notificationBuilderSpy).getImageFromUrl(anyString());

        pushServiceManagerSpy.processMessage(bundle);
    }

    // this class is because Mockito cannot mock anonymous classes
    class MySwrveNotificationFilter implements SwrveNotificationFilter {
        @Override
        public Notification filterNotification(NotificationCompat.Builder builder, int id, SwrveNotificationDetails notificationDetails, String jsonPayload) {
            return builder.build();
        }
    }

    @Test
    public void testInternalKey() throws Exception {
        // If you add an internal root key make sure to add it to PUSH_INTERNAL_KEYS

        ArrayList internalKeys = new ArrayList(SwrveNotificationInternalPayloadConstants.PUSH_INTERNAL_KEYS);
        Field[] fields = SwrveNotificationInternalPayloadConstants.class.getDeclaredFields();

        // Add any internal key to PUSH_INTERNAL_KEYS
        assertEquals(internalKeys.size(), fields.length - 1);

        for (Field f : fields) {
            String fieldName = f.getName();
            if (!fieldName.equals("PUSH_INTERNAL_KEYS")) {
                String fieldValue = (String) f.get(null);
                if (!internalKeys.contains(fieldValue)) {
                    throw new Exception("Please add the field '" + fieldName + "' to SwrveNotificationInternalPayloadConstants.PUSH_INTERNAL_KEYS");
                }
            }
        }
    }

    @Test
    public void testNotificationCustomFilterJsonPayloadCleaned() {
        SwrveNotificationFilter notificationFilterSpy = spy(new MySwrveNotificationFilter());
        SwrveNotificationConfig.Builder notificationConfig = new SwrveNotificationConfig.Builder(dummyIconResource, dummyIconResource, dummyChannel).notificationFilter(notificationFilterSpy);
        SwrveConfig config = new SwrveConfig();
        config.setNotificationConfig(notificationConfig.build());
        SwrveSDK.createInstance(RuntimeEnvironment.application, 1, "apiKey", config);
        
        Bundle bundle = new Bundle();
        String payloadJson = "{}";
        for (String internalKey : SwrveNotificationInternalPayloadConstants.PUSH_INTERNAL_KEYS) {
            if (!internalKey.equals(SwrveNotificationInternalPayloadConstants.SWRVE_AUTH_USER_KEY)) {
                bundle.putString(internalKey, "value");
            }
        }

        bundle.putString(SwrveNotificationConstants.SWRVE_TRACKING_KEY, "1");
        bundle.putString(SwrveNotificationConstants.SWRVE_PAYLOAD_KEY, payloadJson);
        bundle.putString(SwrveNotificationConstants.TEXT_KEY, "plain text");
        bundle.putString(SwrveNotificationConstants.SWRVE_NESTED_JSON_PAYLOAD_KEY, "{ 'customKey': 'customValues' }");
        bundle.putString("customKey", "youShallNotPass");
        bundle.putString("passKey", "passValue");

        SwrvePushServiceManager pushServiceManager = new SwrvePushServiceManager(mActivity);
        pushServiceManager.processMessage(bundle);

        // verify the custom payload is delivered
        verify(notificationFilterSpy, atLeastOnce())
                .filterNotification(any(NotificationCompat.Builder.class), anyInt(), any(SwrveNotificationDetails.class), eq("{\"customKey\":\"customValues\",\"passKey\":\"passValue\"}"));
    }

}
