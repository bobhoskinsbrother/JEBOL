package org.jebol.application;

import org.jebol.domain.eval.CompressionMethod;
import org.jebol.domain.eval.Postbox;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public interface Command {

    List<Value> arguments();

    Set<String> refinements();

    Value evaluate(AnyBlockValue block);

    HandleValue makeHandle(String type, Object held);

    <T> T unwrap(Value handle, Class<T> type);

    Raised failure(ErrorCategory category, String id, Value... arguments);

    /**
     * Makes COMPRESS and DECOMPRESS use this method under this name in this interpreter, as
     * {@code RL_REGISTER_COMPRESS_METHOD} does, and adds a name not seen before to
     * {@code system/catalog/compressions}.
     */
    void registerCompression(String name, CompressionMethod method);

    /** Makes COMPRESS and DECOMPRESS use the built-in method of this name again, undoing any registration. */
    void restoreBuiltInCompression(String name);

    /** What {@code system/<field>/...} holds in this interpreter, or none. */
    Value readSystemField(String field, String... deeper);

    Postbox postbox();
}
