package com.bbthechange.inviter.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for idea-related notification text generation in NotificationTextGenerator.
 * Pure POJO tests -- no mocking needed.
 *
 * Coverage:
 * - getIdeasAddedBody: single/multiple ideas, name fallback
 * - getIdeaListCreatedBody: valid/null name
 * - getFirstInterestBody, getBroadInterestAdderBody, getBroadInterestBody, getGroupConsensusBody
 */
class NotificationTextGeneratorIdeaTest {

    private final NotificationTextGenerator generator = new NotificationTextGenerator();

    // =========================================================================
    // getIdeasAddedBody
    // =========================================================================

    @Nested
    class GetIdeasAddedBody {

        @Test
        void getIdeasAddedBody_singleIdea_showsName() {
            String result = generator.getIdeasAddedBody("Alex", "NYC Restaurants", List.of("Sushi"));

            assertThat(result).isEqualTo("Alex added 'Sushi' to NYC Restaurants");
        }

        @Test
        void getIdeasAddedBody_twoIdeas_listsBoth() {
            String result = generator.getIdeasAddedBody("Alex", "NYC Restaurants",
                    List.of("Sushi", "Pizza"));

            assertThat(result).isEqualTo("Alex added 2 ideas to NYC Restaurants — Sushi and Pizza");
        }

        @Test
        void getIdeasAddedBody_threeIdeas_listsTwoAndMore() {
            String result = generator.getIdeasAddedBody("Alex", "NYC Restaurants",
                    List.of("Sushi", "Pizza", "Tacos"));

            assertThat(result).isEqualTo("Alex added 3 ideas to NYC Restaurants — Sushi, Pizza, and 1 more");
        }

        @Test
        void getIdeasAddedBody_manyIdeas_tapToSee() {
            List<String> ideas = List.of("A", "B", "C", "D", "E", "F", "G");
            String result = generator.getIdeasAddedBody("Alex", "NYC Restaurants", ideas);

            assertThat(result).isEqualTo("Alex added 7 ideas to NYC Restaurants — tap to see what's new");
        }

        @Test
        void getIdeasAddedBody_nullName_fallsBackToSomeone() {
            String result = generator.getIdeasAddedBody(null, "NYC Restaurants", List.of("Sushi"));

            assertThat(result).startsWith("Someone added");
        }

        @Test
        void getIdeasAddedBody_emptyList_gracefulFallback() {
            String result = generator.getIdeasAddedBody(null, "NYC Restaurants", Collections.emptyList());

            assertThat(result).isEqualTo("Someone added ideas to NYC Restaurants");
        }

        @Test
        void getIdeasAddedBody_unknownName_fallsBackToSomeone() {
            String result = generator.getIdeasAddedBody("Unknown", "NYC Restaurants", List.of("Sushi"));

            assertThat(result).startsWith("Someone added");
        }
    }

    // =========================================================================
    // getIdeaListCreatedBody
    // =========================================================================

    @Nested
    class GetIdeaListCreatedBody {

        @Test
        void getIdeaListCreatedBody_validName_formatsCorrectly() {
            String result = generator.getIdeaListCreatedBody("Alex", "NYC Restaurants");

            assertThat(result).isEqualTo("Alex created a new list: NYC Restaurants");
        }

        @Test
        void getIdeaListCreatedBody_nullName_fallsBackToSomeone() {
            String result = generator.getIdeaListCreatedBody(null, "NYC Restaurants");

            assertThat(result).isEqualTo("Someone created a new list: NYC Restaurants");
        }
    }

    // =========================================================================
    // getFirstInterestBody
    // =========================================================================

    @Nested
    class GetFirstInterestBody {

        @Test
        void getFirstInterestBody_validInputs_formatsCorrectly() {
            String result = generator.getFirstInterestBody("Alex", "Sushi");

            assertThat(result).isEqualTo("Alex is also into 'Sushi'");
        }
    }

    // =========================================================================
    // getBroadInterestAdderBody
    // =========================================================================

    @Nested
    class GetBroadInterestAdderBody {

        @Test
        void getBroadInterestAdderBody_formatsCorrectly() {
            String result = generator.getBroadInterestAdderBody("Sushi", 3);

            assertThat(result).isEqualTo("Your idea 'Sushi' is popular — 3 people are interested");
        }
    }

    // =========================================================================
    // getBroadInterestBody
    // =========================================================================

    @Nested
    class GetBroadInterestBody {

        @Test
        void getBroadInterestBody_formatsCorrectly() {
            String result = generator.getBroadInterestBody("Sushi", 3);

            assertThat(result).isEqualTo("3 people want to try Sushi — are you in?");
        }
    }

    // =========================================================================
    // getGroupConsensusBody
    // =========================================================================

    @Nested
    class GetGroupConsensusBody {

        @Test
        void getGroupConsensusBody_formatsCorrectly() {
            String result = generator.getGroupConsensusBody("Sushi");

            assertThat(result).isEqualTo("Most of the group wants to do Sushi — time to make it happen?");
        }
    }
}
