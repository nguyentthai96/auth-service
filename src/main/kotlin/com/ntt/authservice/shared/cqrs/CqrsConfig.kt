package com.ntt.authservice.shared.cqrs

import com.ntt.eventsourcingutils.lib.cqrs.SpringCommandBus
import com.ntt.eventsourcingutils.lib.cqrs.SpringQueryBus
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandBus
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler
import com.ntt.eventsourcingutils.lib.cqrs.query.QueryBus
import com.ntt.eventsourcingutils.lib.cqrs.query.QueryHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * CQRS infrastructure configuration — TEMPORARY OVERRIDE.
 *
 * TODO: REMOVE this class after applying LoggingCommandBus/LoggingQueryBus
 *       to base-cqrs-starter and setting `app.cqrs.logging.enabled=true` in application.yml.
 *       See: base_cqrs_starter_changes.md for the migration code.
 *
 * This @Configuration takes precedence over CqrsAutoConfiguration from base-cqrs-starter
 * because CqrsAutoConfiguration uses @ConditionalOnMissingBean — our explicit @Bean wins.
 *
 * FR-009: SpringCommandBus/SpringQueryBus require explicit @Bean declaration.
 */
@Configuration
class CqrsConfig {

    @Bean
    fun commandBus(handlers: List<CommandHandler<*, *>>): CommandBus =
        LoggingCommandBus(SpringCommandBus(handlers))

    @Bean
    fun queryBus(handlers: List<QueryHandler<*, *>>): QueryBus =
        LoggingQueryBus(SpringQueryBus(handlers))
}
