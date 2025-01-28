package com.example.documentlifecycle;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import io.minio.errors.MinioException;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.TesseractException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Java 21 example for managing document lifecycle states.
 */
public class DocumentLifecycleEngine {

    // --- 1) EXCEPTIONS ---
    public static sealed class DocumentLifecycleException extends RuntimeException
            permits DocumentLifecycleException.Infrastructure, DocumentLifecycleException.Domain {

        public DocumentLifecycleException(String msg, Throwable cause) {
            super(msg, cause);
        }

        public DocumentLifecycleException(String msg) {
            super(msg);
        }

        public static DocumentLifecycleException create(String msg, Throwable cause) {
            return new DocumentLifecycleException(msg, cause);
        }

        public static DocumentLifecycleException create(String msg) {
            return new DocumentLifecycleException(msg);
        }

        public static sealed class Infrastructure extends DocumentLifecycleException
                permits Infrastructure.Storage, Infrastructure.Search, Infrastructure.Ocr, Infrastructure.Messaging {

            private Infrastructure(String msg, Throwable cause) {
                super(msg, cause);
            }

            public static Infrastructure generic(String msg, Throwable cause) {
                return new Infrastructure(msg, cause);
            }

            public static final class Storage extends Infrastructure {
                public Storage(String msg, Throwable cause) {
                    super("Storage error: " + msg, cause);
                }

                public Storage(String msg) {
                    super("Storage error: " + msg, null);
                }
            }

            public static final class Search extends Infrastructure {
                public Search(String msg, Throwable cause) {
                    super("Search error: " + msg, cause);
                }

                public Search(String msg) {
                    super("Search error: " + msg, null);
                }
            }

            public static final class Ocr extends Infrastructure {
                public Ocr(String msg, Throwable cause) {
                    super("OCR error: " + msg, cause);
                }

                public Ocr(String msg) {
                    super("OCR error: " + msg, null);
                }
            }

            public static final class Messaging extends Infrastructure {
                public Messaging(String msg, Throwable cause) {
                    super("Messaging error: " + msg, cause);
                }

                public Messaging(String msg) {
                    super("Messaging error: " + msg, null);
                }
            }
        }

        public static sealed class Domain extends DocumentLifecycleException
                permits Domain.NotFound, Domain.IllegalStateTransition, Domain.Validation {

            private Domain(String msg) {
                super(msg);
            }

            public static final class NotFound extends Domain {
                public NotFound(String docId) {
                    super("Document not found: " + docId);
                }
            }

            public static final class IllegalStateTransition extends Domain {
                public IllegalStateTransition(String state, String event) {
                    super("Cannot transition from state '" + state + "' with event '" + event + "'");
                }
            }

            public static final class Validation extends Domain {
                public Validation(String msg) {
                    super("Validation error: " + msg);
                }

                public Validation(String field, String msg) {
                    super("Validation error: " + field + ": " + msg);
                }
            }
        }
    }

    // --- 2) EVENTS & STATE ---
    public enum DocumentEventType {
        SAVE_TO_DATABASE, SAVE_TO_STORAGE, SAVE_COMPLETE,
        PROCESS_START, PROCESS_COMPLETE, PROCESS_FAILED,
        INDEX_COMPLETE
    }

    public record DocumentEvent(UUID documentId, DocumentEventType eventType, Map<String, Object> metadata) {
        public static DocumentEvent of(UUID id, DocumentEventType t) {
            return new DocumentEvent(id, t, Map.of());
        }

        public static DocumentEvent of(UUID id, DocumentEventType t, Map<String, Object> m) {
            return new DocumentEvent(id, t, m);
        }
    }

    public enum DocumentLifecycleState {
        CREATED {
            public DocumentLifecycleState transition(DocumentEventType e) {
                return switch (e) {
                    case SAVE_TO_DATABASE -> PERSISTING_DATABASE;
                    case PROCESS_FAILED -> FAILED;
                    default -> fail(this, e);
                };
            }
        },
        PERSISTING_DATABASE {
            public DocumentLifecycleState transition(DocumentEventType e) {
                return switch (e) {
                    case SAVE_TO_STORAGE -> PERSISTING_STORAGE;
                    case PROCESS_FAILED -> FAILED;
                    default -> fail(this, e);
                };
            }
        },
        PERSISTING_STORAGE {
            public DocumentLifecycleState transition(DocumentEventType e) {
                return switch (e) {
                    case SAVE_COMPLETE -> SAVED;
                    case PROCESS_FAILED -> FAILED;
                    default -> fail(this, e);
                };
            }
        },
        SAVED {
            public DocumentLifecycleState transition(DocumentEventType e) {
                return switch (e) {
                    case PROCESS_START -> PROCESSING;
                    case PROCESS_FAILED -> FAILED;
                    default -> fail(this, e);
                };
            }
        },
        PROCESSING {
            public DocumentLifecycleState transition(DocumentEventType e) {
                return switch (e) {
                    case PROCESS_COMPLETE -> PROCESSED;
                    case PROCESS_FAILED -> FAILED;
                    default -> fail(this, e);
                };
            }
        },
        PROCESSED {
            public DocumentLifecycleState transition(DocumentEventType e) {
                return switch (e) {
                    case INDEX_COMPLETE -> INDEXED;
                    case PROCESS_FAILED -> FAILED;
                    default -> fail(this, e);
                };
            }
        },
        INDEXED {
            public DocumentLifecycleState transition(DocumentEventType e) {
                return fail(this, e); // Terminal state
            }
        },
        FAILED {
            public DocumentLifecycleState transition(DocumentEventType e) {
                return fail(this, e); // Terminal state
            }
        };

        public abstract DocumentLifecycleState transition(DocumentEventType e);

        private static DocumentLifecycleState fail(DocumentLifecycleState s, DocumentEventType e) {
            throw new DocumentLifecycleException.Domain.IllegalStateTransition(s.name(), e.name());
        }
    }

    // --- 3) ASPECT FOR INFRA EXCEPTIONS ---
    @Aspect
    @Component
    @Slf4j
    public static class InfrastructureExceptionHandlerAspect {
        @Around("within(com.example.infrastructure..*)") 
        public Object handleInfraExceptions(ProceedingJoinPoint pjp) throws Throwable {
            try {
                return pjp.proceed();
            } catch (Exception ex) {
                var op = pjp.getSignature().toShortString();
                var translated = switch (ex) {
                    case MinioException m -> new DocumentLifecycleException.Infrastructure.Storage(op, m);
                    case ElasticsearchException es -> new DocumentLifecycleException.Infrastructure.Search(op, es);
                    case TesseractException t -> new DocumentLifecycleException.Infrastructure.Ocr(op, t);
                    case DocumentLifecycleException dle -> dle;
                    default -> DocumentLifecycleException.Infrastructure.generic("Unexpected error in " + op, ex);
                };
                log.error("Operation failed: {}", translated.getMessage(), translated);
                throw translated;
            }
        }
    }
}
