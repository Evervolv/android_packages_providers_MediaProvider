/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.providers.media;

import android.annotation.NonNull;
import android.content.ContentResolver;
import android.database.CharArrayBuffer;
import android.database.CrossProcessCursorWrapper;
import android.database.Cursor;
import android.database.CursorWindow;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.CancellationSignal;

import com.android.internal.util.ArrayUtils;

import java.util.Objects;

/**
 * Cursor that supports deprecation of {@code _data} columns, typically by
 * replacing values with fake paths that the OS then offers to redirect to
 * {@link ContentResolver#openFileDescriptor(Uri, String)}, which developers
 * should be using directly.
 *
 * @hide
 */
public class TranslatingCursor extends CrossProcessCursorWrapper {
    public static class Config {
        public final Uri baseUri;
        public final String idColumn;
        public final String dataColumn;

        public Config(Uri baseUri, String idColumn, String dataColumn) {
            this.baseUri = baseUri;
            this.idColumn = idColumn;
            this.dataColumn = dataColumn;
        }
    }

    public interface Translator {
        public String translate(String data, long id);
    }

    private final @NonNull Config mConfig;
    private final @NonNull Translator mTranslator;
    private final boolean mDropLast;

    private final int mIdIndex;
    private final int mDataIndex;

    private TranslatingCursor(@NonNull Cursor cursor, @NonNull Config config,
            @NonNull Translator translator, boolean dropLast) {
        super(cursor);

        mConfig = Objects.requireNonNull(config);
        mTranslator = Objects.requireNonNull(translator);
        mDropLast = dropLast;

        mIdIndex = cursor.getColumnIndexOrThrow(config.idColumn);
        mDataIndex = cursor.getColumnIndexOrThrow(config.dataColumn);
    }

    @Override
    public int getColumnCount() {
        if (mDropLast) {
            return super.getColumnCount() - 1;
        } else {
            return super.getColumnCount();
        }
    }

    public static Cursor query(@NonNull Config config, @NonNull Translator translator,
            SQLiteQueryBuilder qb, SQLiteDatabase db, String[] projectionIn, String selection,
            String[] selectionArgs, String groupBy, String having, String sortOrder, String limit,
            CancellationSignal signal) {
        final boolean requestedId = ArrayUtils.isEmpty(projectionIn)
                || ArrayUtils.contains(projectionIn, config.idColumn);
        final boolean requestedData = ArrayUtils.isEmpty(projectionIn)
                || ArrayUtils.contains(projectionIn, config.dataColumn);

        // If caller didn't request data, we have nothing to redirect
        if (!requestedData || !ContentResolver.DEPRECATE_DATA_COLUMNS) {
            return qb.query(db, projectionIn, selection, selectionArgs,
                    groupBy, having, sortOrder, limit, signal);
        }

        // If caller didn't request id, we need to splice it in
        if (!requestedId) {
            projectionIn = ArrayUtils.appendElement(String.class, projectionIn,
                    config.idColumn);
        }

        final Cursor c = qb.query(db, projectionIn, selection, selectionArgs,
                groupBy, having, sortOrder);
        return new TranslatingCursor(c, config, translator, !requestedId);
    }

    @Override
    public void fillWindow(int position, CursorWindow window) {
        // Fill window directly to ensure data is rewritten
        DatabaseUtils.cursorFillWindow(this, position, window);
    }

    @Override
    public CursorWindow getWindow() {
        // Returning underlying window risks leaking data
        return null;
    }

    @Override
    public Cursor getWrappedCursor() {
        throw new UnsupportedOperationException(
                "Returning underlying cursor risks leaking data");
    }

    @Override
    public double getDouble(int columnIndex) {
        if (columnIndex == mDataIndex) {
            throw new IllegalArgumentException();
        } else {
            return super.getDouble(columnIndex);
        }
    }

    @Override
    public float getFloat(int columnIndex) {
        if (columnIndex == mDataIndex) {
            throw new IllegalArgumentException();
        } else {
            return super.getFloat(columnIndex);
        }
    }

    @Override
    public int getInt(int columnIndex) {
        if (columnIndex == mDataIndex) {
            throw new IllegalArgumentException();
        } else {
            return super.getInt(columnIndex);
        }
    }

    @Override
    public long getLong(int columnIndex) {
        if (columnIndex == mDataIndex) {
            throw new IllegalArgumentException();
        } else {
            return super.getLong(columnIndex);
        }
    }

    @Override
    public short getShort(int columnIndex) {
        if (columnIndex == mDataIndex) {
            throw new IllegalArgumentException();
        } else {
            return super.getShort(columnIndex);
        }
    }

    @Override
    public String getString(int columnIndex) {
        if (columnIndex == mDataIndex) {
            return mTranslator.translate(super.getString(mDataIndex), super.getLong(mIdIndex));
        } else {
            return super.getString(columnIndex);
        }
    }

    @Override
    public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
        if (columnIndex == mDataIndex) {
            throw new IllegalArgumentException();
        } else {
            super.copyStringToBuffer(columnIndex, buffer);
        }
    }

    @Override
    public byte[] getBlob(int columnIndex) {
        if (columnIndex == mDataIndex) {
            throw new IllegalArgumentException();
        } else {
            return super.getBlob(columnIndex);
        }
    }

    @Override
    public int getType(int columnIndex) {
        if (columnIndex == mDataIndex) {
            return Cursor.FIELD_TYPE_STRING;
        } else {
            return super.getType(columnIndex);
        }
    }

    @Override
    public boolean isNull(int columnIndex) {
        if (columnIndex == mDataIndex) {
            return getString(columnIndex) == null;
        } else {
            return super.isNull(columnIndex);
        }
    }
}
