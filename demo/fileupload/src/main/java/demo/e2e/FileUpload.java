package demo.e2e;

import act.Act;
import act.job.OnAppStart;
import org.osgl.$;
import org.osgl.mvc.annotation.GetAction;
import org.osgl.mvc.annotation.PostAction;
import org.osgl.util.IO;
import org.osgl.util.S;

import java.io.File;

@SuppressWarnings("unused")
public class FileUpload {

    private static File base = new File("base");

    @OnAppStart
    public void ensureBase() {
        if (!base.exists()) {
            base.mkdir();
        }
    }

    @PostAction
    public String upload(File file) {
        String id = $.randomStr();
        String extension = S.fileExtension(file.getName());
        String newFileName = S.blank(extension) ? id : (id + "." + extension);
        File copy = new File(base, newFileName);
        IO.write(file).to(copy);
        return newFileName;
    }

    @GetAction("{fileName}")
    public File get(String fileName) {
        return new File(base, fileName);
    }

    public static void main(String[] args) throws Exception {
        Act.start();
    }

}
