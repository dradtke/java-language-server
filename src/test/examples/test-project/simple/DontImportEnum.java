package simple;

import java.nio.file.AccessMode;

class DontImportEnum {

    void test() {
        var x = AccessMode.READ;
        var y = new ArrayList<>();
    }

}