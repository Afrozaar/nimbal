
appender("STDOUT", ConsoleAppender) {
  encoder(PatternLayoutEncoder) {
    pattern = "%d %-5level [%thread] %logger{50}.%method - %msg%n"
  }
}

logger("com.afrozaar", DEBUG)
logger("com.afrozaar.nimbal", TRACE)
logger("org.springframework.context", TRACE)
logger("org.eclipse.aether", DEBUG)

root(INFO, ["STDOUT"])