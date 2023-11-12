package net.typeblog.shelter.services;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

public class PaymentStubService extends HostApduService {
    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        // We do not handle anything
        notifyUnhandled();
        return null;
    }

    @Override
    public void onDeactivated(int reason) {

    }
}
