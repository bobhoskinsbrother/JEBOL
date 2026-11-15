package org.jebol.domain.eval.blend2d;

interface PixelSource {

    int composite(int destination, int across, int down);

    int compositeUnderConstantMask(int destination, int mask, int across, int down);

    int compositeUnderVariableMask(int destination, int mask, int across, int down);

    int compositeUnderAnalyticMask(int destination, int mask, int across, int down);
}
