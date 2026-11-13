package cn.goutongban.aac

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.goutongban.aac.data.ModelKeyStore
import cn.goutongban.aac.data.ModelSettings
import cn.goutongban.aac.data.PreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelKeyDeviceTest {
    @Test fun encryptedModelKeyCanBeReadAfterSettingsReload() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = PreferencesStore(context)
        val previous = store.model.first()
        val key = "test-credential"
        val encrypted = ModelKeyStore().encrypt(key)
        assertNotEquals(encrypted, ModelKeyStore().encrypt(key))
        assertFalse(encrypted.contains(key))
        try {
            store.saveModel(ModelSettings(true, "https://example.com/v1", "demo", encrypted))
            val reloaded = PreferencesStore(context).model.first()
            assertEquals("demo", reloaded.model)
            assertEquals(key, ModelKeyStore().decrypt(reloaded.encryptedKey))
        } finally {
            store.saveModel(previous)
        }
    }
}
