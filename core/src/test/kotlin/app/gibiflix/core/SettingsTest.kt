package app.gibiflix.core

import app.gibiflix.core.device.Profile
import app.gibiflix.core.settings.ProfileDefaults
import app.gibiflix.core.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test fun defaultsPerProfile() {
        assertTrue(ProfileDefaults.of(Profile.TABLET).image.isIdentity)
        assertEquals(6, ProfileDefaults.of(Profile.EINK_COLOR).fullRefreshEvery)
        assertEquals(10, ProfileDefaults.of(Profile.EINK_MONO).fullRefreshEvery)
        assertTrue(ProfileDefaults.of(Profile.EINK_MONO).image.grayscale)
    }

    @Test fun roundTripAndOverride() {
        val s = UserSettings().withProfile(Profile.EINK_MONO, ProfileDefaults.of(Profile.EINK_MONO).copy(fullRefreshEvery = 3))
        val back = UserSettings.decode(UserSettings.encode(s))
        assertEquals(3, back.forProfile(Profile.EINK_MONO).fullRefreshEvery)
        assertEquals(6, back.forProfile(Profile.EINK_COLOR).fullRefreshEvery)
        assertEquals(UserSettings(), UserSettings.decode("lixo"))
    }
}
