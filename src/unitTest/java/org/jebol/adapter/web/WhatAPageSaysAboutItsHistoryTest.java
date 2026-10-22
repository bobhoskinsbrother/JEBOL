package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class WhatAPageSaysAboutItsHistoryTest {

    @Test
    @DisplayName("a fresh load names the index the browser kept and the address it was opened at")
    void aFreshLoadIsAnArrival() {
        PageEvent said = new PageEvent("""
                {"type":"arrive","index":3,"address":"survey%203"}""");

        assertThat(said.isArrival()).isTrue();
        assertThat(said.isMove()).isFalse();
        assertThat(said.readHistoryIndex()).contains(3);
        assertThat(said.readAddress()).contains("survey%203");
    }

    @Test
    @DisplayName("a popstate names the entry it reached")
    void aPopstateIsAMove() {
        PageEvent said = new PageEvent("""
                {"type":"moved","index":0,"address":""}""");

        assertThat(said.isMove()).isTrue();
        assertThat(said.isArrival()).isFalse();
        assertThat(said.readHistoryIndex()).contains(0);
        assertThat(said.readAddress()).contains("");
    }

    @Test
    @DisplayName("the type is matched exactly, case and all")
    void theTypeIsExact() {
        assertThat(new PageEvent("""
                {"type":"Arrive","index":0,"address":""}""").isArrival()).isFalse();
        assertThat(new PageEvent("""
                {"type":"MOVED","index":0,"address":""}""").isMove()).isFalse();
        assertThat(new PageEvent("""
                {"type":"measure","wide":1,"high":1}""").isMove()).isFalse();
    }

    @Test
    @DisplayName("index nought is an entry, and the largest whole number is too")
    void theIndexAtItsBoundaries() {
        assertThat(new PageEvent("""
                {"type":"moved","index":0,"address":"a"}""").readHistoryIndex()).contains(0);
        assertThat(new PageEvent("""
                {"type":"moved","index":2147483647,"address":"a"}""").readHistoryIndex())
                .contains(Integer.MAX_VALUE);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"-1", "1.5", "true", "null", "[1]", "2147483648"})
    @DisplayName("an index that is not a whole number of nought or more is no index at all")
    void aWrongIndexIsNone(String index) {
        assertThat(new PageEvent("""
                {"type":"moved","index":%s,"address":"a"}""".formatted(index))
                .readHistoryIndex()).isEmpty();
    }

    @Test
    @DisplayName("a number sent as text is not an index")
    void aQuotedIndexIsNone() {
        assertThat(new PageEvent("""
                {"type":"moved","index":"3","address":"a"}""").readHistoryIndex()).isEmpty();
    }

    @Test
    @DisplayName("a missing index is no index")
    void aMissingIndexIsNone() {
        assertThat(new PageEvent("""
                {"type":"moved","address":"a"}""").readHistoryIndex()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"3", "true", "null", "[1]"})
    @DisplayName("an address that is not text is no address, never a coerced one")
    void aWrongAddressIsNone(String address) {
        assertThat(new PageEvent("""
                {"type":"moved","index":1,"address":%s}""".formatted(address))
                .readAddress()).isEmpty();
    }

    @Test
    @DisplayName("a list holding text is not an address")
    void aListOfTextIsNone() {
        assertThat(new PageEvent("""
                {"type":"moved","index":1,"address":["a"]}""").readAddress()).isEmpty();
    }

    @Test
    @DisplayName("a missing address is no address, and an extra field is ignored")
    void aMissingAddressIsNone() {
        assertThat(new PageEvent("""
                {"type":"moved","index":1}""").readAddress()).isEmpty();
        assertThat(new PageEvent("""
                {"type":"moved","index":1,"address":"a","colour":"red"}""").readAddress()).contains("a");
    }
}
