package com.coshian.tradeeverything.advancement;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class AllItemsProgressionTest {
	@Test void groupsSpecialVanillaItemsAndExcludesMods() {
		assertEquals("item__minecraft__diamond", AllItemsProgression.criterion(Identifier.withDefaultNamespace("diamond")));
		assertEquals(AllItemsProgression.ENCHANTED_BOOK, AllItemsProgression.key(Identifier.withDefaultNamespace("enchanted_book")).orElseThrow());
		assertEquals(AllItemsProgression.POTION, AllItemsProgression.key(Identifier.withDefaultNamespace("potion")).orElseThrow());
		assertEquals(AllItemsProgression.POTION, AllItemsProgression.key(Identifier.withDefaultNamespace("splash_potion")).orElseThrow());
		assertEquals(AllItemsProgression.POTION, AllItemsProgression.key(Identifier.withDefaultNamespace("lingering_potion")).orElseThrow());
		assertTrue(AllItemsProgression.key(Identifier.parse("examplemod:ruby")).isEmpty());
	}
	@Test void criterionEncodingIsStableAndDistinct() {
		assertEquals("special__enchanted_book", AllItemsProgression.criterion(AllItemsProgression.ENCHANTED_BOOK));
		assertEquals("special__potion", AllItemsProgression.criterion(AllItemsProgression.POTION));
		assertNotEquals(AllItemsProgression.criterion(Identifier.withDefaultNamespace("diamond")), AllItemsProgression.criterion(Identifier.withDefaultNamespace("stone")));
	}
	@Test void anyConfiguredModdedIdRemainsOutsideTheCanonicalUniverse() {
		var configured = java.util.List.of(Identifier.parse("examplemod:ruby"), Identifier.parse("anothermod:sapphire"), Identifier.parse("thirdmod:machine_part"));
		assertEquals(0L, configured.stream().filter(id -> AllItemsProgression.key(id).isPresent()).count(), "Zero, one, or many configured mod IDs must never create all-items progress units");
	}
}
