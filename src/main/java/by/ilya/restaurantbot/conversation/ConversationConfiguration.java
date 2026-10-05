package by.ilya.restaurantbot.conversation;

import javax.sql.DataSource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class ConversationConfiguration {
    @Bean
    JdbcChatMemoryRepository chatMemoryRepository(DataSource source, PlatformTransactionManager manager) {
        return JdbcChatMemoryRepository.builder().dataSource(source).transactionManager(manager).build();
    }

    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder().chatMemoryRepository(repository).maxMessages(20).build();
    }
}
