package com.sneva.spng.assist;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

import ar.com.hjg.pngj.ChunkReader;
import ar.com.hjg.pngj.ChunkSeqReaderPng;
import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.PngHelperInternal;
import ar.com.hjg.pngj.PngReader;
import ar.com.hjg.pngj.PngjException;
import ar.com.hjg.pngj.chunks.ChunkHelper;
import ar.com.hjg.pngj.chunks.ChunkRaw;
import ar.com.hjg.pngj.chunks.PngChunk;
import ar.com.hjg.pngj.chunks.PngChunkACTL;
import ar.com.hjg.pngj.chunks.PngChunkFCTL;
import ar.com.hjg.pngj.chunks.PngChunkFDAT;
import ar.com.hjg.pngj.chunks.PngChunkIDAT;
import ar.com.hjg.pngj.chunks.PngChunkIEND;
import ar.com.hjg.pngj.chunks.PngChunkIHDR;

public class ApngExtractFrames {

    static class PngReaderBuffered extends PngReader {
        private final File orig;

        public PngReaderBuffered(File file) {
            super(file);
            this.orig = file;
        }

        FileOutputStream fo = null;
        File dest;
        ImageInfo frameInfo;
        int frameIndex = -1;

        @Override
        protected ChunkSeqReaderPng createChunkSeqReader() {
            return new ChunkSeqReaderPng(false) {
                @Override
                public boolean shouldSkipContent(int len, String id) {
                    return false;
                }

                @Override
                protected boolean isIdatKind(String id) {
                    return false;
                }

                @Override
                protected void postProcessChunk(ChunkReader chunkR) {
                    super.postProcessChunk(chunkR);
                    try {
                        String id = chunkR.getChunkRaw().id;
                        PngChunk lastChunk = chunksList.getChunks().get(chunksList.getChunks().size() - 1);
                        if (id.equals(PngChunkFCTL.ID)) {
                            frameIndex++;
                            frameInfo = ((PngChunkFCTL) lastChunk).getEquivImageInfo();
                            startNewFile();
                        }
                        if (id.equals(PngChunkFDAT.ID) || id.equals(PngChunkIDAT.ID)) {
                            if (id.equals(PngChunkIDAT.ID)) {
                                if (fo != null)
                                    chunkR.getChunkRaw().writeChunk(fo);
                            } else {
                                ChunkRaw crawi =
                                        new ChunkRaw(chunkR.getChunkRaw().len - 4, ChunkHelper.b_IDAT, true);
                                System.arraycopy(chunkR.getChunkRaw().data, 4, crawi.data, 0, crawi.data.length);
                                crawi.writeChunk(fo);
                            }
                            chunkR.getChunkRaw().data = null;
                        }
                        if (id.equals(PngChunkIEND.ID)) {
                            if (fo != null)
                                endFile();
                        }
                    } catch (Exception e) {
                        throw new PngjException(e);
                    }
                }
            };
        }

        private void startNewFile() throws Exception {
            if (fo != null) endFile();
            dest = createOutputName();
            fo = new FileOutputStream(dest);
            fo.write(PngHelperInternal.getPngIdSignature());
            PngChunkIHDR ihdr = new PngChunkIHDR(frameInfo);
            ihdr.createRawChunk().writeChunk(fo);

            for (PngChunk chunk : getChunksList(false).getChunks()) {
                String id = chunk.id;
                if (id.equals(PngChunkIHDR.ID) || id.equals(PngChunkFCTL.ID) || id.equals(PngChunkACTL.ID)) {
                    continue;
                }
                if (id.equals(PngChunkIDAT.ID)) {
                    break;
                }
                chunk.getRaw().writeChunk(fo);
            }
        }

        private void endFile() throws IOException {
            new PngChunkIEND(null).createRawChunk().writeChunk(fo);
            fo.close();
            fo = null;
        }

        private File createOutputName() {
            return new File(orig.getParent(), getFileName(orig, frameIndex));
        }
    }

    public static String getFileName(File sourceFile, int frameIndex) {
        String filename = sourceFile.getName();
        String baseName = FilenameUtils.getBaseName(filename);
        String extension = FilenameUtils.getExtension(filename);
        return String.format(Locale.ENGLISH, "%s_%03d.%s", baseName, frameIndex, extension);
    }

    public static int process(final File orig) {
        PngReaderBuffered pngr = new PngReaderBuffered(orig);
        pngr.end();
        return pngr.frameIndex + 1;
    }
}
