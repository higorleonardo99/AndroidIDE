package com.itsaky.androidide.utils;

import com.blankj.utilcode.util.FileIOUtils;
import com.blankj.utilcode.util.FileUtils;
import com.itsaky.androidide.models.SearchResult;
import com.itsaky.androidide.tasks.TaskExecutor;
import com.itsaky.lsp.Position;
import com.itsaky.lsp.Range;
import io.github.rosemoe.editor.text.CharPosition;
import io.github.rosemoe.editor.text.Content;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;

/**
 * This class provides API to search in files recursively
 *
 * @author Akash Yadav
 */
public class RecursiveFileSearcher {
    
    private static final Logger logger = Logger.instance("RecursiveFileSearcher");
    
    /**
     * Search the given text in files recursively in given search directories
     *
     * @param text Text to search
     * @param exts Extentions of file to search. Maybe null.
     * @param searchDirs Directories to search in. Subdirectories will be included
     * @param callback A listener that will listen to the search result
     */
    public static void searchRecursiveAsync(String text, List<String> exts, List<File> searchDirs, Callback callback) {
        // Cannot search empty or null text
        if(text == null || text.isEmpty()) {
            return;
        }
        
        // If there is no listener to the search, search is meaningless
        if(callback == null) {
            return;
        }
           
        // Avoid searching if no directories are specified
        if(searchDirs == null || searchDirs.isEmpty()) {
            return;
        }
        
        new TaskExecutor().executeAsync(new Searcher(text, exts, searchDirs), result -> callback.onResult(result));
    }
    
    private static class Searcher implements Callable<Map<File, List<SearchResult>>> {
        
        private final String query;
        private final List<String> exts;
        private final List<File> dirs;

        public Searcher(String query, List<String> exts, List<File> dirs) {
            this.query = query;
            this.exts = exts;
            this.dirs = dirs;
        }
        
        @Override
        public Map<File, List<SearchResult>> call() throws Exception {
            final Map<File, List<SearchResult>> result = new HashMap<>();
            for(int i=0;i<dirs.size();i++) {
                final File dir = dirs.get(i);
                final List<File> files = FileUtils.listFilesInDirWithFilter(dir, new MultiFileFilter(exts), true);
                for(int j=0;files != null && j < files.size();j++) {
                    final File file = files.get(j);
                    if(file.isDirectory()) continue;
                    final String text = FileIOUtils.readFile2String(file);
                    if(text == null || text.trim().isEmpty()) continue;
                    final Content content = new Content(null, text);
                    final List<SearchResult> ranges = new ArrayList<>();
                    Matcher matcher = Pattern.compile(Pattern.quote(this.query)).matcher(text);
                    while(matcher.find()) {
                        final Range range = new Range();
                        final CharPosition start = content.getIndexer().getCharPosition(matcher.start());
                        final CharPosition end = content.getIndexer().getCharPosition(matcher.end());
                        range.start = new Position(start.line, start.column);
                        range.end = new Position(end.line, end.column);
                        String sub = "...".concat(text.substring(Math.max(0, matcher.start() - 30), Math.min(matcher.end() + 31, text.length()))).trim().concat("...");
                        String match = content.subContent(start.line, start.column, end.line, end.column).toString();
                        ranges.add(new SearchResult(range, file, sub.replaceAll("\\s+", " "), match));
                    }
                    if(ranges.size() > 0) {
                        result.put(file, ranges);
                    }
                }
            }
            return result;
        }
    }
    
    private static class MultiFileFilter implements FileFilter {
        
        private final List<String> exts;

        public MultiFileFilter(List<String> exts) {
            this.exts = exts;
        }
        
        @Override
        public boolean accept(File file) {
            boolean accept = false;
            if(exts == null || exts.isEmpty() || file.isDirectory()) {
                accept = true;
            } else {
                for(String ext : exts) {
                    if(file.getName().endsWith(ext)) {
                        accept = true;
                        break;
                    }
                }
            }
            
            return accept && FileUtils.isUtf8(file);
        }
    }
    
    public static interface Callback {
        void onResult(Map<File, List<SearchResult>> results);
    }
}