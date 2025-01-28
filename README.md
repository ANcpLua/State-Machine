# README – Document Lifecycle State Machine

**Overview:**
![Untitled diagram-2025-01-28-055316](https://github.com/user-attachments/assets/0c4f2281-9b72-455f-b11b-4781176cff33)

This code focuses on demonstrating the state transitions, exception handling, and how an Aspect can be used to manage infrastructure errors.

**Contents:**

1.  [DocumentLifecycleException](#1-documentlifecycleexception)
2.  [DocumentLifecycleState](#2-documentlifecyclestate)
3.  [DocumentEvent / DocumentEventType](#3-documentevent-and-documenteventtype)
4.  [InfrastructureExceptionHandlerAspect](#4-infrastructureexceptionhandleraspect)

## 1. DocumentLifecycleException

*   A sealed class hierarchy for all possible errors.
*   Divided into two main branches:
    *   **Infrastructure:** Represents technical failures from external systems (e.g., MinIO, Elasticsearch, OCR tools, Messaging).
    *   **Domain:** Represents business logic issues (e.g., invalid state transitions, missing documents, validations).
*   Example:
    *   `Domain.IllegalStateTransition` is thrown for invalid state changes.
    *   `Infrastructure.Storage` wraps errors related to storage systems.

    The `Infrastructure` branch can be extended to include other technical errors, such as `Messaging` for message queue issues.

## 2. DocumentLifecycleState

*   An enum representing each possible stage of a document's lifecycle: `CREATED`, `PERSISTING_DATABASE`, `PERSISTING_STORAGE`, `SAVED`, `PROCESSING`, `PROCESSED`, `INDEXED`, and `FAILED`.
*   Each state overrides a `transition` method specifying which events cause valid movement to a new state.
*   Invalid events for a state throw a `Domain.IllegalStateTransition` exception.
*   Terminal states (`INDEXED`, `FAILED`) do not allow further transitions.

## 3. DocumentEvent and DocumentEventType

*   **DocumentEventType:** An enum listing all recognized events:
    *   `SAVE_TO_DATABASE`, `SAVE_TO_STORAGE`, `SAVE_COMPLETE`
    *   `PROCESS_START`, `PROCESS_COMPLETE`, `PROCESS_FAILED`
    *   `INDEX_COMPLETE`
*   **DocumentEvent:** A record that bundles the event with a `documentId` and optional metadata.
*   These events trigger lifecycle transitions (e.g., `PERSISTING_STORAGE` → `SAVED` upon `SAVE_COMPLETE`).

## 4. InfrastructureExceptionHandlerAspect

*   An AspectJ component that intercepts calls within the `com.example.infrastructure` package (where infrastructure interactions are assumed to reside).
*   Translates caught exceptions into appropriate `Infrastructure.*` subclasses, ensuring only domain-aware exceptions propagate.
*   Enhances maintainability by handling and wrapping external library errors consistently.

**Summary:**

*   The code enforces valid state transitions, preventing illegal changes.
*   Domain exceptions highlight business logic concerns (e.g., illegal transitions or missing documents).
*   Infrastructure exceptions uniformly wrap external (library) issues.
*   This design provides a structured approach to building a document management feature with robust error handling and logical flow control.
