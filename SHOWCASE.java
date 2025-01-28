public enum DocumentState {
    CREATED,
    PERSISTING_DATABASE,
    PERSISTING_STORAGE,
    SAVED,
    PROCESSING,
    PROCESSED,
    INDEXED,
    FAILED;

    public DocumentState transition(DocumentEventType event) {
        switch (this) {
            case CREATED:
                switch (event) {
                    case SAVE_TO_DATABASE:
                        return PERSISTING_DATABASE;
                    case PROCESS_FAILED:
                        return FAILED;
                    default:
                        throw new IllegalStateTransitionException(this.name(), event.name());
                }
            case PERSISTING_DATABASE:
                switch (event) {
                    case SAVE_TO_STORAGE:
                        return PERSISTING_STORAGE;
                    case PROCESS_FAILED:
                        return FAILED;
                    default:
                        throw new IllegalStateTransitionException(this.name(), event.name());
                }
            case PERSISTING_STORAGE:
                switch (event) {
                    case SAVE_COMPLETE:
                        return SAVED;
                    case PROCESS_FAILED:
                        return FAILED;
                    default:
                        throw new IllegalStateTransitionException(this.name(), event.name());
                }
            case SAVED:
                switch (event) {
                    case PROCESS_START:
                        return PROCESSING;
                    case PROCESS_FAILED:
                        return FAILED;
                    default:
                        throw new IllegalStateTransitionException(this.name(), event.name());
                }
            case PROCESSING:
                switch (event) {
                    case PROCESS_COMPLETE:
                        return PROCESSED;
                    case PROCESS_FAILED:
                        return FAILED;
                    default:
                        throw new IllegalStateTransitionException(this.name(), event.name());
                }
            case PROCESSED:
                switch (event) {
                    case INDEX_COMPLETE:
                        return INDEXED;
                    case PROCESS_FAILED:
                        return FAILED;
                    default:
                        throw new IllegalStateTransitionException(this.name(), event.name());
                }
            case INDEXED:
            case FAILED:
                throw new IllegalStateTransitionException(this.name(), event.name());
            default:
                throw new IllegalStateTransitionException(this.name(), event.name());
        }
    }
}
