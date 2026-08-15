package com.wenyan.app.container

import android.content.Context
import com.wenyan.app.BuildConfig
import com.wenyan.app.data.datastore.SettingsRepository as DataStoreSettings
import com.wenyan.app.data.db.AppDatabase
import com.wenyan.app.data.image.ImageCompressor
import com.wenyan.app.data.repository.ConversationRepository
import com.wenyan.app.data.repository.ProfileRepository
import com.wenyan.app.data.repository.ProviderRepository
import com.wenyan.app.data.security.KeystoreAesGcmCipher
import com.wenyan.app.data.update.UpdateChecker
import com.wenyan.app.data.update.UpdateClient
import com.wenyan.app.knowledge.AndroidKnowledgeAssetReader
import com.wenyan.app.knowledge.KnowledgeEngine
import com.wenyan.app.log.CrashLogStore
import com.wenyan.app.prompt.PromptBuilder
import com.wenyan.app.ui.contract.AppContainer
import okhttp3.OkHttpClient

/**
 * 真实依赖注入容器（装配层，只组装零业务）：
 * Room DB + DataStore + Keystore 加密 + LLM Client 工厂 + 知识引擎 + PromptBuilder。
 * v1.7.3：装配 memory_fact DAO、CrashLogStore（崩溃日志）、UpdateChecker（更新检查）。
 * 由 WenyanApp 持有，供各 ViewModel 注入。
 */
class RealAppContainer(
    context: Context,
    private val crashLogStore: CrashLogStore,
) : AppContainer {

    private val appContext = context.applicationContext
    private val database: AppDatabase = AppDatabase.get(appContext)
    private val dataStore: DataStoreSettings = DataStoreSettings(appContext)
    private val cipher: KeystoreAesGcmCipher = KeystoreAesGcmCipher()

    private val providerRepository = ProviderRepository(
        database.providerDao(), database.modelDao(), cipher
    )
    private val profileRepository = ProfileRepository(
        database.profileDao(), database.targetDao(), database.memoryFactDao(), database
    )
    private val conversationRepository = ConversationRepository(
        database.sessionDao(), database.messageDao()
    )

    /** v1.7.3 T4 更新检查（GitHub Releases 直连 + OkHttp 下载）；v1.7.3-fix：只传 versionName，不传本地刻度 versionCode */
    private val updateChecker = UpdateChecker(
        client = UpdateClient(OkHttpClient()),
        currentVersionName = BuildConfig.VERSION_NAME,
        okHttp = OkHttpClient(),
    )

    override val settingsRepository: com.wenyan.app.ui.contract.SettingsRepository =
        RealSettingsRepository(
            context = appContext,
            dataStore = dataStore,
            providerRepository = providerRepository,
            profileRepository = profileRepository,
            conversationRepository = conversationRepository,
            crashLogStore = crashLogStore,
            updateChecker = updateChecker,
        )

    override val onboardingRepository: com.wenyan.app.ui.contract.OnboardingRepository =
        RealOnboardingRepository(dataStore, profileRepository)

    override val chatRepository: com.wenyan.app.ui.contract.ChatRepository =
        RealChatRepository(
            context = appContext,
            dataStore = dataStore,
            conversationRepository = conversationRepository,
            profileRepository = profileRepository,
            providerRepository = providerRepository,
            knowledgeEngine = KnowledgeEngine(AndroidKnowledgeAssetReader(appContext)),
            promptBuilder = PromptBuilder(),
            imageCompressor = ImageCompressor(),
        )
}
