package id.web.agentvaedah.strideway.data.remote.llm

enum class LlmProviderId(
    val label: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val keyRequired: Boolean = true,
) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.0-flash"),
    GROQ("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1", "claude-sonnet-5-5"),
    OLLAMA("Ollama / 9Router", "http://192.168.1.10:11434/v1", "llama3.1", keyRequired = false),
}

data class LlmSettings(
    val provider: LlmProviderId = LlmProviderId.OPENAI,
    val model: String = provider.defaultModel,
    val baseUrl: String = provider.defaultBaseUrl,
)

data class LlmMessage(val role: String, val content: String)

sealed interface LlmResult {
    data class Ok(val text: String) : LlmResult
    data class Failure(val reason: String) : LlmResult
}
