package com.biteExpress;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BiteExpressApplicationTests {
	@Value("${local.server.port}")
	private int port;

	@Test
	void startsHttpServer() {
		assertTrue(port > 0);
	}

}
