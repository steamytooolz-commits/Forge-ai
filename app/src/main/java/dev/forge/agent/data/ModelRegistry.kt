package dev.forge.agent.data

object ModelRegistry {
    val ALL_MODELS: List<ModelInfo> = CuratedModels.all

    fun getModel(id: String): ModelInfo? = ALL_MODELS.firstOrNull { it.id == id }
    fun getModelsForProvider(kind: ProviderConfig.Kind): List<ModelInfo> = ALL_MODELS.filter { it.providerKind == kind }
}
