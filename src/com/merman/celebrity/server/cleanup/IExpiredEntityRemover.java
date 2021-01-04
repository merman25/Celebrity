package com.merman.celebrity.server.cleanup;

import java.util.function.Consumer;

public interface IExpiredEntityRemover<T extends ICanExpire> extends Consumer<T> {
	public void remove(T aCanExpire);

	@Override
	default void accept(T aCanExpire) {
		remove(aCanExpire);
	}
}
