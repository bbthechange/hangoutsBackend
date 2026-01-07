package com.bbthechange.inviter.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientInfoTest {

    @Nested
    class FromRequest {

        @Test
        void allHeaders_ExtractsAllFields() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-App-Version", "1.2.3");
            request.addHeader("X-Build-Number", "45");
            request.addHeader("X-Client-Type", "ios");
            request.addHeader("X-Device-ID", "device-123");
            request.addHeader("User-Agent", "HangoutApp/1.2.3");

            // When
            ClientInfo clientInfo = ClientInfo.fromRequest(request);

            // Then
            assertThat(clientInfo.appVersion()).isEqualTo("1.2.3");
            assertThat(clientInfo.buildNumber()).isEqualTo("45");
            assertThat(clientInfo.clientType()).isEqualTo("ios");
            assertThat(clientInfo.deviceId()).isEqualTo("device-123");
            assertThat(clientInfo.userAgent()).isEqualTo("HangoutApp/1.2.3");
            assertThat(clientInfo.platform()).isEqualTo("ios");
        }

        @Test
        void noHeaders_ReturnsNullFields() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();

            // When
            ClientInfo clientInfo = ClientInfo.fromRequest(request);

            // Then
            assertThat(clientInfo.appVersion()).isNull();
            assertThat(clientInfo.buildNumber()).isNull();
            assertThat(clientInfo.clientType()).isNull();
            assertThat(clientInfo.deviceId()).isNull();
            assertThat(clientInfo.platform()).isEqualTo("web"); // Default
        }

        @Test
        void mobileClientType_DerivesPlatformFromUserAgent() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Client-Type", "mobile");
            request.addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)");

            // When
            ClientInfo clientInfo = ClientInfo.fromRequest(request);

            // Then
            assertThat(clientInfo.clientType()).isEqualTo("mobile");
            assertThat(clientInfo.platform()).isEqualTo("ios");
        }

        @Test
        void mobileClientType_AndroidUserAgent_DerivesAndroid() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Client-Type", "mobile");
            request.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14)");

            // When
            ClientInfo clientInfo = ClientInfo.fromRequest(request);

            // Then
            assertThat(clientInfo.platform()).isEqualTo("android");
        }
    }

    @Nested
    class IsMobile {

        @Test
        void mobileClientType_ReturnsTrue() {
            ClientInfo info = new ClientInfo("1.0", "1", "mobile", null, null, "ios");
            assertThat(info.isMobile()).isTrue();
        }

        @Test
        void iosClientType_ReturnsTrue() {
            ClientInfo info = new ClientInfo("1.0", "1", "ios", null, null, "ios");
            assertThat(info.isMobile()).isTrue();
        }

        @Test
        void androidClientType_ReturnsTrue() {
            ClientInfo info = new ClientInfo("1.0", "1", "android", null, null, "android");
            assertThat(info.isMobile()).isTrue();
        }

        @Test
        void webClientType_ReturnsFalse() {
            ClientInfo info = new ClientInfo("1.0", "1", "web", null, null, "web");
            assertThat(info.isMobile()).isFalse();
        }

        @Test
        void nullClientType_ReturnsFalse() {
            ClientInfo info = new ClientInfo("1.0", "1", null, null, null, "web");
            assertThat(info.isMobile()).isFalse();
        }
    }

    @Nested
    class IsIos {

        @Test
        void iosClientType_ReturnsTrue() {
            ClientInfo info = new ClientInfo(null, null, "ios", null, null, "ios");
            assertThat(info.isIos()).isTrue();
        }

        @Test
        void iosPlatform_ReturnsTrue() {
            ClientInfo info = new ClientInfo(null, null, "mobile", null, null, "ios");
            assertThat(info.isIos()).isTrue();
        }

        @Test
        void iPhoneUserAgent_ReturnsTrue() {
            ClientInfo info = new ClientInfo(null, null, null, null, "Mozilla (iPhone)", "web");
            assertThat(info.isIos()).isTrue();
        }

        @Test
        void androidClientType_ReturnsFalse() {
            ClientInfo info = new ClientInfo(null, null, "android", null, null, "android");
            assertThat(info.isIos()).isFalse();
        }
    }

    @Nested
    class IsAndroid {

        @Test
        void androidClientType_ReturnsTrue() {
            ClientInfo info = new ClientInfo(null, null, "android", null, null, "android");
            assertThat(info.isAndroid()).isTrue();
        }

        @Test
        void androidPlatform_ReturnsTrue() {
            ClientInfo info = new ClientInfo(null, null, "mobile", null, null, "android");
            assertThat(info.isAndroid()).isTrue();
        }

        @Test
        void androidUserAgent_ReturnsTrue() {
            ClientInfo info = new ClientInfo(null, null, null, null, "Mozilla (Android 14)", "web");
            assertThat(info.isAndroid()).isTrue();
        }

        @Test
        void iosClientType_ReturnsFalse() {
            ClientInfo info = new ClientInfo(null, null, "ios", null, null, "ios");
            assertThat(info.isAndroid()).isFalse();
        }
    }

    @Nested
    class IsVersionAtLeast {

        @Test
        void nullVersion_ReturnsTrue() {
            ClientInfo info = new ClientInfo(null, null, "ios", null, null, "ios");
            assertThat(info.isVersionAtLeast("1.0.0")).isTrue();
        }

        @Test
        void sameVersion_ReturnsTrue() {
            ClientInfo info = new ClientInfo("1.2.3", null, "ios", null, null, "ios");
            assertThat(info.isVersionAtLeast("1.2.3")).isTrue();
        }

        @Test
        void higherVersion_ReturnsTrue() {
            ClientInfo info = new ClientInfo("2.0.0", null, "ios", null, null, "ios");
            assertThat(info.isVersionAtLeast("1.2.3")).isTrue();
        }

        @Test
        void lowerVersion_ReturnsFalse() {
            ClientInfo info = new ClientInfo("1.0.0", null, "ios", null, null, "ios");
            assertThat(info.isVersionAtLeast("1.2.3")).isFalse();
        }

        @Test
        void higherMinorVersion_ReturnsTrue() {
            ClientInfo info = new ClientInfo("1.3.0", null, "ios", null, null, "ios");
            assertThat(info.isVersionAtLeast("1.2.3")).isTrue();
        }

        @Test
        void lowerPatchVersion_ReturnsFalse() {
            ClientInfo info = new ClientInfo("1.2.2", null, "ios", null, null, "ios");
            assertThat(info.isVersionAtLeast("1.2.3")).isFalse();
        }

        @Test
        void twoPartVersion_ComparedAgainstThreePart() {
            ClientInfo info = new ClientInfo("1.2", null, "ios", null, null, "ios");
            assertThat(info.isVersionAtLeast("1.2.0")).isTrue();
            assertThat(info.isVersionAtLeast("1.2.1")).isFalse();
        }

        @Test
        void betaVersion_IgnoresSuffix() {
            ClientInfo info = new ClientInfo("1.2.3-beta", null, "ios", null, null, "ios");
            assertThat(info.isVersionAtLeast("1.2.3")).isTrue();
        }
    }

    @Nested
    class ToLogString {

        @Test
        void fullInfo_FormatsCorrectly() {
            ClientInfo info = new ClientInfo("1.2.3", "45", "ios", "device-123", "Agent", "ios");
            assertThat(info.toLogString()).isEqualTo("ios/1.2.3 (45)");
        }

        @Test
        void noBuildNumber_OmitsBuildNumber() {
            ClientInfo info = new ClientInfo("1.2.3", null, "ios", null, null, "ios");
            assertThat(info.toLogString()).isEqualTo("ios/1.2.3");
        }

        @Test
        void noVersion_ShowsClientTypeOnly() {
            ClientInfo info = new ClientInfo(null, null, "ios", null, null, "ios");
            assertThat(info.toLogString()).isEqualTo("ios");
        }

        @Test
        void noClientType_UsesPlatform() {
            ClientInfo info = new ClientInfo("1.0.0", null, null, null, null, "ios");
            assertThat(info.toLogString()).isEqualTo("ios/1.0.0");
        }

        @Test
        void noClientTypeOrPlatform_ShowsUnknown() {
            ClientInfo info = new ClientInfo(null, null, null, null, null, null);
            assertThat(info.toLogString()).isEqualTo("unknown");
        }
    }

    @Nested
    class FromRequestAttribute {

        @Test
        void attributeSet_ReturnsClientInfo() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            ClientInfo expected = new ClientInfo("1.0", "1", "ios", null, null, "ios");
            request.setAttribute(ClientInfo.REQUEST_ATTRIBUTE, expected);

            // When
            ClientInfo actual = ClientInfo.fromRequestAttribute(request);

            // Then
            assertThat(actual).isSameAs(expected);
        }

        @Test
        void attributeNotSet_ReturnsNull() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();

            // When
            ClientInfo actual = ClientInfo.fromRequestAttribute(request);

            // Then
            assertThat(actual).isNull();
        }
    }
}
