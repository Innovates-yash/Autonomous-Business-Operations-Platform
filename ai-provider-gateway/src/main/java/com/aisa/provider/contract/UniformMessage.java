package com.aisa.provider.contract;

import java.util.Objects;

/**
 * A single message in a uniform conversation request.
 *
 * <p>This structure is identical for every provider so that the request field set does not
 * vary by provider (Requirement 20.4). Provider clients (task 7.2) translate this into their
 * own SDK message types.
 *
 * @param role    the speaker role for this message
 * @param content the message text; must be non-null
 */
public record UniformMessage(UniformRole role, String content) {

    public UniformMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }

    public static UniformMessage system(String content) {
        return new UniformMessage(UniformRole.SYSTEM, content);
    }

    public static UniformMessage user(String content) {
        return new UniformMessage(UniformRole.USER, content);
    }

    public static UniformMessage assistant(String content) {
        return new UniformMessage(UniformRole.ASSISTANT, content);
    }
}
