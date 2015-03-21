package com.mastfrog.giulius.mongojack;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.acteur.mongo.GiuliusMongoModule;
import com.mastfrog.acteur.mongo.MongoInitializer;
import com.mongodb.DBCollection;
import java.util.LinkedList;
import java.util.List;
import org.mongojack.JacksonDBCollection;

/**
 *
 * @author Tim Boudreau
 */
public class MongoJacksonModule extends AbstractModule {

    private final List<Entry<?, ?>> entries = new LinkedList<>();

    private final GiuliusMongoModule mongo;

    MongoJacksonModule(String name) {
        mongo = new GiuliusMongoModule(name);
    }

    public MongoJacksonModule addInitializer(Class<? extends MongoInitializer> type) {
        mongo.addInitializer(type);
        return this;
    }

    public final <T, R> MongoJacksonModule bindCollection(String bindingName, TypeLiteral<JacksonDBCollection<T, R>> tl, Class<T> left, Class<R> right) {
        bindCollection(bindingName, bindingName, tl, left, right);
        return this;
    }

    public final <T, R> MongoJacksonModule bindCollection(String bindingName, String collectionName, TypeLiteral<JacksonDBCollection<T, R>> tl, Class<T> left, Class<R> right) {
        mongo.bindCollection(bindingName, collectionName);
        entries.add(new Entry<>(bindingName, tl, left, right));
        return this;
    }

    public final String getDatabaseName() {
        return mongo.getDatabaseName();
    }

    @Override
    protected void configure() {
        install(mongo);
        Binder binder = binder();
        for (Entry<?,?> e : entries) {
            e.bind(binder);
        }
        entries.clear();
    }

    private static final class Entry<T, R> {

        private final String bindingName;
        private final TypeLiteral<JacksonDBCollection<T, R>> tl;
        private final Class<T> left;
        private final Class<R> right;

        public Entry(String bindingName, TypeLiteral<JacksonDBCollection<T, R>> tl, Class<T> left, Class<R> right) {
            this.bindingName = bindingName;
            this.tl = tl;
            this.left = left;
            this.right = right;
        }

        void bind(Binder binder) {
            Named anno = Names.named(bindingName);
            Provider<DBCollection> collectionProvider = binder.getProvider(Key.get(DBCollection.class, anno));
            Provider<JacksonDBCollection<T, R>> result = new JacksonDBCollectionProvider<>(collectionProvider, left, right);
            binder.bind(tl).toProvider(result);
        }
    }

    private static final class JacksonDBCollectionProvider<T, R> implements Provider<JacksonDBCollection<T, R>> {

        private final Provider<DBCollection> dbCollection;
        private final Class<T> left;
        private final Class<R> right;

        JacksonDBCollectionProvider(Provider<DBCollection> dbCollection, Class<T> left, Class<R> right) {
            this.dbCollection = dbCollection;
            this.left = left;
            this.right = right;
        }

        @Override
        public JacksonDBCollection<T, R> get() {
            DBCollection coll = dbCollection.get();
            return JacksonDBCollection.wrap(coll, left, right);
        }
    }
}
