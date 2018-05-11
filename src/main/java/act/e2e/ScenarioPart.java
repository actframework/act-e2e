package act.e2e;

import org.osgl.exception.UnexpectedException;

public interface ScenarioPart {
    /**
     * Check if the data is valid.
     *
     * If the data is not valid then throw out {@link UnexpectedException}
     *
     * @throws {@link UnexpectedException} if the data is not valid
     */
    void validate() throws UnexpectedException;
}
