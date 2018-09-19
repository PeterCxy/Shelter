// IFileShuttleServiceCallback.aidl
package net.typeblog.shelter.services;

import net.typeblog.shelter.services.IFileShuttleService;

interface IFileShuttleServiceCallback {
    void callback(in IFileShuttleService service);
}
