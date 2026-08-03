package com.goutoujunshi.app.container

import android.content.Context
import com.goutoujunshi.app.data.datastore.SettingsRepository as DataStoreSettings
import com.goutoujunshi.app.data.db.AppDatabase
import com.goutoujunshi.app.data.image.ImageCompressor
import com.goutoujunshi.app.data.repository.ConversationRepository
import com.goutoujunshi.app.data.repository.ProfileRepository
import com.goutoujunshi.app.data.repository.ProviderRepository
import com.goutoujunshi.app.data.security.KeystoreAesGcmCipher
import com.goutoujunshi.app.knowledge.AndroidKnowledgeAssetReader
import com.goutoujunshi.app.knowledge.KnowledgeEngine
import com.goutoujunshi.app.prompt.PromptBuilder
import com.goutoujunshi.app.ui.contract.AppContainer

/**
 * 真实依赖注入容器（装配层，只组装零业务）：
 * Room DB + DataStore + Keystore 加密 + LLM Client 工厂 + 知识引擎 + PromptBuilder。
 * 由 GoutoujunshiApp 持有，供各 ViewModel 注入。
 */
class RealAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext
    private val database: AppDatabase = AppDatabase.get(appContext)
    private val dataStore: DataStoreSettings = DataStoreSettings(appContext)
    private val cipher: KeystoreAesGcmCipher = KeystoreAesGcmCipher()

    private val providerRepository = ProviderRepository(
        database.providerDao(), database.modelDao(), cipher
    )
    private val profileRepository = ProfileRepository(
        database.profileDao(), database.targetDao()
    )
    private val conversationRepository = ConversationRepository(
        database.sessionDao(), database.messageDao()
    )

    override val settingsRepository: com.goutoujunshi.app.ui.contract.SettingsRepository =
        RealSettingsRepository(dataStore, providerRepository, profileRepository, conversationRepository)

    override val onboardingRepository: com.goutoujunshi.app.ui.contract.OnboardingRepository =
        RealOnboardingRepository(dataStore, profileRepository)

    override val chatRepository: com.goutoujunshi.app.ui.contract.ChatRepository =
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
