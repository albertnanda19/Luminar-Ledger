package io.luminar.ledger;

import org.springframework.boot.SpringApplication;

public class TestLuminarLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.from(LuminarLedgerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
