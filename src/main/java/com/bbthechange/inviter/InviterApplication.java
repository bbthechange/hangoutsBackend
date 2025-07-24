package com.bbthechange.inviter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class InviterApplication {

	public static void main(String[] args) {
		System.out.println("=== DEBUG: Starting minimal Spring Boot web application ===");
		
		SpringApplication app = new SpringApplication(InviterApplication.class);
		app.run(args);
		
		System.out.println("=== DEBUG: SpringApplication.run() completed ===");
	}
	
	@EventListener(WebServerInitializedEvent.class)
	public void onWebServerReady(WebServerInitializedEvent event) {
		System.out.println("=== DEBUG: *** WEB SERVER STARTED ON PORT " + event.getWebServer().getPort() + " *** ===");
		System.out.println("=== DEBUG: Server namespace: " + event.getApplicationContext().getServerNamespace() + " ===");
	}

}
