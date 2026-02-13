package main.java.com.example.screenagent;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "screen.save")
@Getter
@Setter
public class ScreenConfig {

    private String dir;
}
