package ge.kursi.settlement;

import ge.kursi.settlement.config.FundingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FundingProperties.class)
public class SettlementFundingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementFundingApplication.class, args);
    }
}
