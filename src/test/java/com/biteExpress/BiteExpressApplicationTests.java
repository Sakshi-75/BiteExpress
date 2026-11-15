package com.biteExpress;

import com.biteExpress.importer.SampleRestaurantImport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BiteExpressApplicationTests {
	@Value("${local.server.port}")
	private int port;
	@Autowired
	private ApplicationContext context;

	@Test
	void startsHttpServer() {
		assertTrue(port > 0);
		assertEquals(0, context.getBeansOfType(SampleRestaurantImport.class).size());
	}

}
