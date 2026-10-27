package app.gibiflix.core.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDetectorTest {
    private fun info(man: String, brand: String = man, model: String = "", product: String = "", device: String = "") =
        BuildInfo(man, brand, model, device, product)

    @Test fun samsungIsTablet() {
        assertEquals(Profile.TABLET, ProfileDetector.detect(info("samsung", model = "SM-X730")))
    }

    @Test fun onyxWithColorInModelIsColor() {
        assertEquals(Profile.EINK_COLOR, ProfileDetector.detect(info("ONYX", "Onyx", model = "Go Color 7")))
        assertEquals(Profile.EINK_COLOR, ProfileDetector.detect(info("Onyx", product = "GoColor7")))
    }

    @Test fun onyxBrandBooxWithoutColorIsMono() {
        assertEquals(Profile.EINK_MONO, ProfileDetector.detect(info("Unknown", "BOOX", model = "Go7")))
    }

    @Test fun knownColorModelWithoutWordColor() {
        assertEquals(Profile.EINK_COLOR, ProfileDetector.detect(info("onyx", model = "NoteAir3C")))
    }

    @Test fun resolveRespectsManualChoiceThenConfirmation() {
        assertEquals(Profile.EINK_MONO, ProfileDetector.resolve(Profile.TABLET, ProfileChoice.EINK_MONO, Profile.EINK_COLOR))
        assertEquals(Profile.EINK_COLOR, ProfileDetector.resolve(Profile.EINK_MONO, ProfileChoice.AUTO, Profile.EINK_COLOR))
        assertEquals(Profile.TABLET, ProfileDetector.resolve(Profile.TABLET, ProfileChoice.AUTO, null))
    }

    @Test fun profileFlags() {
        assertTrue(Profile.EINK_MONO.isEink && Profile.EINK_MONO.isMono)
        assertFalse(Profile.TABLET.isEink)
    }
}
