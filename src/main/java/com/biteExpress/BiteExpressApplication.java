package com.biteExpress;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "BiteExpress Restaurant Discovery API", version = "v1",
        description = "Create restaurants and search by structured filters, distance, or supported natural-language phrases."))
public class BiteExpressApplication {

	public static void main(String[] args) {
		SpringApplication.run(BiteExpressApplication.class, args);
	}

}
