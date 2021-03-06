package com.atg.openssp.core.cache.type;

import com.atg.openssp.common.cache.MapCache;
import com.atg.openssp.common.dto.VideoAd;

/**
 * @author André Schmer
 *
 */
public final class VideoAdDataCache extends MapCache<Integer, VideoAd> {

	public static final VideoAdDataCache instance = new VideoAdDataCache();

	private VideoAdDataCache() {
		super();
	}

}
