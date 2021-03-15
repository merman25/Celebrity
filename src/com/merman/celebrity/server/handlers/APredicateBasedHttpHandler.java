package com.merman.celebrity.server.handlers;

import java.util.function.Predicate;

import com.merman.celebrity.server.HTTPExchange;

public abstract class APredicateBasedHttpHandler extends AHttpHandler {
	private Predicate<HTTPExchange> conditionToHandleExchange;
	
	public APredicateBasedHttpHandler(Predicate<HTTPExchange> aConditionToHandleExchange) {
		setConditionToHandleExchange(aConditionToHandleExchange);
	}
	
	public boolean shouldHandle(HTTPExchange aHTTPExchange) {
		return getConditionToHandleExchange().test(aHTTPExchange);
	}

	protected Predicate<HTTPExchange> getConditionToHandleExchange() {
		return conditionToHandleExchange;
	}

	protected void setConditionToHandleExchange(Predicate<HTTPExchange> aConditionToHandleExchange) {
		if (aConditionToHandleExchange == null) {
			throw new IllegalArgumentException("Predicate may not be null");
		}
		conditionToHandleExchange = aConditionToHandleExchange;
	}
}
