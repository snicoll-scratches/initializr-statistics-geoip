package io.spring.initializr.geoip

import io.searchbox.client.JestClient
import io.searchbox.client.JestClientFactory
import io.searchbox.client.config.HttpClientConfig

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@SpringBootApplication
@EnableConfigurationProperties(GeoIpProperties)
class GeoIpApplication {

	static void main(String[] args) {
		new SpringApplicationBuilder(GeoIpApplication)
				.profiles('local').run(args)
	}

	@Bean
	JestClient jestClient(GeoIpProperties properties) {
		return createJestClient(properties.elasticUrl, null, null)
	}

	private static JestClient createJestClient(String url, String username, String password) {
		JestClientFactory factory = new JestClientFactory();
		def builder = new HttpClientConfig.Builder(url).multiThreaded(true)
		if (username) {
			builder.defaultCredentials(username, password)
		}
		factory.setHttpClientConfig(builder.build());
		factory.getObject();
	}

}
