package demo.e2e;

import act.inject.DefaultValue;
import org.joda.time.DateTime;
import org.osgl.$;
import org.osgl.mvc.annotation.Action;
import org.osgl.mvc.annotation.GetAction;
import org.osgl.util.C;
import org.osgl.util.E;

@SuppressWarnings("unused")
public class Service {

    /**
     * The hello (`/hello`) endpoint.
     *
     * This will accept a query parameter named `who` and
     * return a greeting string in a form of "Hello $who"
     *
     * @param who
     *      request query parameter to specify the hello target.
     *      default value is `World`.
     * @return A hello string
     */
    @GetAction("hello")
    public String hello(@DefaultValue("World") String who) {
        return "Hello " + who;
    }

    /**
     * Returns an important date in history: 09/Mar/2016.
     *
     * [AlphaGo](https://en.wikipedia.org/wiki/AlphaGo), a computer program defeated
     * [Lee Sedol](https://en.wikipedia.org/wiki/Lee_Sedol), one of the best players at Go
     * at this date.
     *
     * @return an important date in the history
     */
    @GetAction("date")
    public DateTime date() {
        return DateTime.parse("2016-03-09");
    }

    /**
     * Returns an int array with first element be `start` and the last element be `end` minus one.
     *
     * ### Edge cases
     *
     * *  If `end` equals to `start` then an empty array is returned.
     * *  If `end` is less than `start` then 400 Bad request is returned
     *
     * @param start the lower bound
     * @param end the upper bound (exclusive)
     * @return an int array as described above
     */
    @Action("array/int")
    public int[] intArray(int start, int end) {
        E.illegalArgumentIf(end < start, "`end` must no be less than `start`");
        return $.copy(C.range(start, end)).to(new int[end - start]);
    }

}
