// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.tools.ant.beautifier;

import org.apache.tools.ant.*;
import org.apache.tools.ant.taskdefs.*;
import java.io.*;
import java.util.*;

/**
 * Ant task fixing known HTML issues for Javadoc.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridJavadocAntTask extends MatchingTask {
    /** I/O buffer size. */
    private static final int BUF_SIZE = 1024 << 3;

    /** directory. */
    private File dir;

    /** CSS file name. */
    private String css;

    /** I/O buffer. */
    private final char[] ioBuf = new char[BUF_SIZE];

    /**
     * Sets directory.
     *
     * @param dir Directory to set.
     */
    public void setDir(File dir) {
        assert dir != null;

        this.dir = dir;
    }

    /**
     * Sets CSS file name.
     * @param css CSS file name to set.
     */
    public void setCss(String css) {
        assert css != null;

        this.css = css;
    }

    /**
     * Closes resource.
     *
     * @param closeable Resource to close.
     */
    private void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            }
            catch (IOException e) {
                log("Failed closing [resource=" + closeable + ", message=" + e.getLocalizedMessage() + ']',
                    Project.MSG_WARN);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public void execute() {
        if (dir == null) {
            throw new BuildException("'dir' attribute must be specified.");
        }

        if (css == null) {
            throw new BuildException("'css' attribute must be specified.");
        }

        log("dir=" + dir, Project.MSG_DEBUG);
        log("css=" + css, Project.MSG_DEBUG);

        DirectoryScanner scanner = getDirectoryScanner(dir);

        for (String fileName : scanner.getIncludedFiles()) {
            String file = dir.getAbsolutePath() + '/' + fileName;

            try {
                processFile(file);
            }
            catch (IOException e) {
                throw new BuildException("IO error while processing: " + file, e);
            }
        }
    }

    /**
     * Processes file (cleaning up Javadoc's HTML).
     *
     * @param file File to cleanup.
     * @throws IOException Thrown in case of any I/O error.
     */
    private void processFile(String file) throws IOException {
        assert file != null;

        CharArrayWriter writer = new CharArrayWriter();

        Reader reader = null;

        try {
            reader = new FileReader(file);

            for (int i; (i = reader.read(ioBuf)) != -1; writer.write(ioBuf, 0, i)) {
                // No-op.
            }
        }
        finally {
            close(reader);
        }

        GridJavadocCharArrayLexReader lexer = new GridJavadocCharArrayLexReader(writer.toCharArray());

        Collection<GridJavadocToken> toks = new ArrayList<GridJavadocToken>();

        StringBuilder tokBuf = new StringBuilder();

        int ch;

        while ((ch = lexer.read()) != GridJavadocCharArrayLexReader.EOF) {
            // Instruction, tag or comment.
            if (ch =='<') {
                if (tokBuf.length() > 0) {
                    toks.add(new GridJavadocToken(GridJavadocTokenType.TOKEN_TEXT, tokBuf.toString()));

                    tokBuf.setLength(0);
                }

                tokBuf.append('<');

                ch = lexer.read();

                if (ch == GridJavadocCharArrayLexReader.EOF) {
                    throw new IOException("Unexpected EOF: " + file);
                }

                // Instruction or comment.
                if (ch == '!') {
                    for (; ch != GridJavadocCharArrayLexReader.EOF && ch != '>'; ch = lexer.read()) {
                        tokBuf.append((char)ch);
                    }

                    if (ch == GridJavadocCharArrayLexReader.EOF) {
                        throw new IOException("Unexpected EOF: " + file);
                    }

                    assert ch == '>';

                    tokBuf.append('>');

                    String value = tokBuf.toString();

                    toks.add(new GridJavadocToken(value.startsWith("<!--") ? GridJavadocTokenType.TOKEN_COMM :
                        GridJavadocTokenType.TOKEN_INSTR, value));

                    tokBuf.setLength(0);
                }
                // Tag.
                else {
                    for (; ch != GridJavadocCharArrayLexReader.EOF && ch != '>'; ch = lexer.read()) {
                        tokBuf.append((char)ch);
                    }

                    if (ch == GridJavadocCharArrayLexReader.EOF) {
                        throw new IOException("Unexpected EOF: " + file);
                    }

                    assert ch == '>';

                    tokBuf.append('>');

                    if (tokBuf.length() <= 2) {
                        throw new IOException("Invalid HTML in [file=" + file + ", html=" + tokBuf + ']');
                    }

                    String value = tokBuf.toString();

                    toks.add(new GridJavadocToken(value.startsWith("</") ?
                        GridJavadocTokenType.TOKEN_CLOSE_TAG : GridJavadocTokenType.TOKEN_OPEN_TAG, value));

                    tokBuf.setLength(0);
                }
            }
            else {
                tokBuf.append((char)ch);
            }
        }

        if (tokBuf.length() > 0) {
            toks.add(new GridJavadocToken(GridJavadocTokenType.TOKEN_TEXT, tokBuf.toString()));
        }

        boolean inZone = false;
        boolean addCss = false;

        Stack<Boolean> stack = new Stack<Boolean>();

        for (GridJavadocToken tok : toks) {
            String val = tok.value();

            switch (tok.type()) {
                case TOKEN_COMM: {
                    if ("<!-- ========= END OF TOP NAVBAR ========= -->".equals(val)) {
                        inZone = true;
                    }
                    else if ("<!-- ======= START OF BOTTOM NAVBAR ====== -->".equals(val)) {
                        inZone = false;
                    }

                    break;
                }

                case TOKEN_OPEN_TAG: {
                    if (inZone) {
                        if (val.startsWith("<TABLE")) {
                            addCss = !val.contains("BORDER=\"0\"");

                            stack.push(addCss);

                            if (addCss) {
                                tok.update("<TABLE CLASS=\"" + css + '\"' + val.substring(6));
                            }
                        }
                        else if (val.startsWith("<TD")) {
                            if (addCss) {
                                tok.update("<TD CLASS=\"" + css + '\"' + val.substring(3));
                            }
                        }
                        else if (val.startsWith("<TH")) {
                            if (addCss) {
                                tok.update("<TH CLASS=\"" + css + '\"' + val.substring(3));
                            }
                        }
                        else if (val.startsWith("<HR SIZE=\"4\" NOSHADE>")) {
                            tok.update("");
                        }
                    }

                    tok.update(fixColors(tok.value()));

                    break;
                }

                case TOKEN_CLOSE_TAG: {
                    if ("</head>".equalsIgnoreCase(val)) {
                        tok.update(
                            "<link type='text/css' rel='stylesheet' href='http://www.gridgain.com/sh3.0/styles/shCore.css'/>\n" +
                            "<link type='text/css' rel='stylesheet' href='http://www.gridgain.com/sh3.0/styles/shThemeEclipse.css'/>\n" +
                            "<script type='text/javascript' src='http://www.gridgain.com/sh3.0/src/shCore.js'></script>\n" +
                            "<script type='text/javascript' src='http://www.gridgain.com/sh3.0/src/shLegacy.js'></script>\n" +
                            "<script type='text/javascript' src='http://www.gridgain.com/sh3.0/scripts/shBrushJava.js'></script>\n" +
                            "<script type='text/javascript' src='http://www.gridgain.com/sh3.0/scripts/shBrushPlain.js'></script>\n" +
                            "<script type='text/javascript' src='http://www.gridgain.com/sh3.0/scripts/shBrushJScript.js'></script>\n" +
                            "<script type='text/javascript' src='http://www.gridgain.com/sh3.0/scripts/shBrushXml.js'></script>\n" +
                            "<script type='text/javascript' src='http://www.gridgain.com/sh3.0/scripts/shBrushScala.js'></script>\n" +
                            "<script type='text/javascript' src='http://www.gridgain.com/sh3.0/scripts/shBrushGroovy.js'></script>\n" +
                            "</head>\n");
                    }
                    else if ("</body>".equalsIgnoreCase(val)) {
                        tok.update(
                            "<!--FOOTER-->" +
                            "<script type='text/javascript'>" +
                                "SyntaxHighlighter.all();" +
                                "dp.SyntaxHighlighter.HighlightAll('code');" +
                            "</script>\n" +
                            "</body>\n");
                    }
                    else if (inZone) {
                        if (val.startsWith("</TABLE")) {
                            stack.pop();

                            addCss = stack.isEmpty() || stack.peek();
                        }
                    }

                    break;
                }

                case TOKEN_INSTR: {
                    // No-op.

                    break;
                }
                
                case TOKEN_TEXT: {
                    tok.update(fixColors(val));

                    break;
                }

                default: {
                    assert false;
                }
            }
        }

        StringBuilder buf = new StringBuilder();
        StringBuilder tmp = new StringBuilder();

        boolean inPre = false;

        // Second pass for unstructured replacements.
        for (GridJavadocToken tok : toks) {
            String val = tok.value();

            switch (tok.type()) {
                case TOKEN_INSTR:
                case TOKEN_TEXT:
                case TOKEN_COMM: {
                    tmp.append(val);

                    break;
                }

                case TOKEN_OPEN_TAG: {
                    if (val.toLowerCase().startsWith("<pre name=")) {
                        inPre = true;

                        buf.append(fixBrackets(tmp.toString()));

                        tmp.setLength(0);
                    }

                    tmp.append(val);

                    break;
                }

                case TOKEN_CLOSE_TAG: {
                    if (val.toLowerCase().startsWith("</pre") && inPre) {
                        inPre = false;
                        
                        buf.append(tmp.toString());

                        tmp.setLength(0);
                    }

                    tmp.append(val);

                    break;
                }

                default: {
                    assert false;
                }
            }
        }

        String s = buf.append(fixBrackets(tmp.toString())).toString();

        s = fixExternalLinks(s);
        s = fixDeprecated(s);
        s = fixNullable(s);
        s = fixTodo(s);

        replaceFile(file, s);
    }

    /**
     *
     * @param s String token.
     * @return Token with replaced colors.
     */
    private String fixColors(String s) {
        return s.replace("0000c0", "000000").
            replace("000000", "333333").
            replace("c00000", "333333").
            replace("008000", "999999").
            replace("990000", "336699").
            replace("font color=\"#808080\"", "font size=-2 color=\"#aaaaaa\"");
    }

    /**
     *
     * @param s String token.
     * @return Fixed token value.
     */
    private String fixBrackets(String s) {
        return s.replace("&lt;", "<span class='angle_bracket'>&lt;</span>").
            replace("&gt;", "<span class='angle_bracket'>&gt;</span>");
    }

    /**
     *
     * @param s String token.
     * @return Fixed token value.
     */
    private String fixTodo(String s) {
        return s.replace("TODO", "<span class='todo'>TODO</span>");
    }

    /**
     *
     * @param s String token.
     * @return Fixed token value.
     */
    private String fixNullable(String s) {
        //                <FONT SIZE="-1">@Nullable</FONT>
        return s.replace("<FONT SIZE=\"-1\">@Nullable", "<FONT SIZE=\"-1\" class='nullable'>@Nullable");
    }

    /**
     *
     * @param s String token.
     * @return Fixed token value.
     */
    private String fixDeprecated(String s) {
        return s.replace("<B>Deprecated.</B>", "<span class='deprecated'>Deprecated.</span>");
    }

    /**
     *
     * @param s String token.
     * @return Fixed token value.
     */
    private String fixExternalLinks(String s) {
        return s.replace("A HREF=\"http://java.sun.com/j2se/1.6.0",
            "A target='jse5javadoc' HREF=\"http://java.sun.com/j2se/1.6.0");
    }

    /**
     * Replaces file with given body.
     *
     * @param file File to replace.
     * @param body New body for the file.
     * @throws IOException Thrown in case of any errors.
     */
    private void replaceFile(String file, String body) throws IOException {
        OutputStream out = null;

        try {
            out = new BufferedOutputStream(new FileOutputStream(file));

            out.write(body.getBytes());
        }
        finally {
            close(out);
        }
    }
}
