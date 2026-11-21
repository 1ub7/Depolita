package Depolita;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Application {
	static final String SERVER_TIME_ZONE = "Asia/Seoul";

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(SERVER_TIME_ZONE));
		SpringApplication.run(Application.class, args);
	}

}
