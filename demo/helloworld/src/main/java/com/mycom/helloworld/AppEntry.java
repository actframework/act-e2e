package com.mycom.helloworld;

import act.Act;
import act.inject.DefaultValue;
import act.util.Output;
import org.osgl.mvc.annotation.GetAction;

/**
 * A simple hello world app entry
 *
 * Run this app, try to update some of the code, then
 * press F5 in the browser to watch the immediate change
 * in the browser!
 */
@SuppressWarnings("unused")
public class AppEntry {

    /**
     * The home (`/`) endpoint.
     *
     * This will accept a query parameter named `who` and
     * render a template (resources/rythm/__package__/AppEntry/home.html),
     * where `__package__` corresponding to the package name, e.g.
     * if your package is `com.mycomp.myproj`, then `__package__`
     * is `com/mycomp/myproj`.
     *
     * @param who
     *      request query parameter to specify the hello target.
     *      default value is `World`.
     */
    @GetAction
    public void home(@DefaultValue("World") @Output String who) {
    }

    public static void main(String[] args) throws Exception {
        Act.start();
    }

}
