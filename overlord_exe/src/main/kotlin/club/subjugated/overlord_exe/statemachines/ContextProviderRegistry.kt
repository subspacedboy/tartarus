package club.subjugated.overlord_exe.statemachines

import org.springframework.stereotype.Component

@Component
class ContextProviderRegistry(
    providers: List<ContextProvider<*, *>>
) {
    private val providerMap = providers.associateBy { it.javaClass.name }

    fun getProvider(className: String): ContextProvider<*, *> {
        return providerMap[className]
            ?: throw IllegalArgumentException("No provider registered for class: $className")
    }
}