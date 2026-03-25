package app.salary.calculator.client;

import app.salary.rules.RulePack;

import java.util.Optional;

public interface RulePackClient {
    Optional<RulePack> fetchLatest(String country, int taxYear);
}
