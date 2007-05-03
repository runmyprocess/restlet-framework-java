/*
 * Copyright 2005-2006 Noelios Consulting.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 *
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * http://www.opensource.org/licenses/cddl1.txt
 * If applicable, add the following below this CDDL
 * HEADER, with the fields enclosed by brackets "[]"
 * replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 * 
 * Portions Copyright 2006 Lars Heuer (heuer[at]semagia.com)
 */

package com.noelios.restlet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.restlet.AbstractChainlet;
import org.restlet.Call;
import org.restlet.component.Component;
import org.restlet.data.Encoding;
import org.restlet.data.EncodingPref;
import org.restlet.data.Encodings;
import org.restlet.data.MediaType;
import org.restlet.data.MediaTypes;
import org.restlet.data.Representation;

import com.noelios.restlet.data.EncoderRepresentation;

/**
 * Chainlet compressing input or output representations. The best encoding is automatically 
 * selected based on the preferences of the client and on the encoding supported by NRE: GZip, Zip and 
 * Deflate.<br/>
 * If the {@link org.restlet.data.Representation} has an unknown size, it will always be a candidate for
 * encoding. Candidate representations need to respect media type criteria by the lists of accepted and
 * ignored media types. 
 * @author Lars Heuer (heuer[at]semagia.com) <a href="http://semagia.com/">Semagia</a>
 * @author Jerome Louvel (contact@noelios.com) <a href="http://www.noelios.com">Noelios Consulting</a>
 */
public class CompressChainlet extends AbstractChainlet
{
	/**
	 * Indicates if the encoding should always occur, regardless of the size. 
	 */
	public static final int ALWAYS_ENCODE = -1;
	
	/**
	 * Indicates if the input representation should be encoded.
	 */
	protected boolean encodeInput;
	
	/**
	 * Indicates if the output representation should be encoded.
	 */
	protected boolean encodeOutput;
	
	/**
	 * The minimal size necessary for encoding.
	 */
	protected long mininumSize;

	/**
	 * The media types that should be encoded.
	 */
	protected List<MediaType> acceptedMediaTypes;

	/**
	 * The media types that should be ignored.
	 */
	protected List<MediaType> ignoredMediaTypes;

	/**
	 * Constructor using the default media types and with {@link #ALWAYS_ENCODE} setting.
	 * This constructor will only encode output representations after call handling.
	 * @param parent The parent component.
	 */
	public CompressChainlet(Component parent)
	{
		this(parent, false, true, ALWAYS_ENCODE, getDefaultAcceptedMediaTypes(),
				getDefaultIgnoredMediaTypes());
	}

	/**
	 * Constructor.
	 * @param parent The parent component.
	 * @param encodeInput Indicates if the input representation should be encoded.
	 * @param encodeOutput Indicates if the output representation should be encoded.
	 * @param minimumSize The minimal size of the representation where compression should be used.
	 * @param acceptedMediaTypes The media types that should be encoded.
	 * @param ignoredMediaTypes The media types that should be ignored.
	 */
	public CompressChainlet(Component parent, boolean encodeInput, boolean encodeOutput, 
			long minimumSize, List<MediaType> acceptedMediaTypes, List<MediaType> ignoredMediaTypes)
	{
		super(parent);
		this.encodeInput = encodeInput;
		this.encodeOutput = encodeOutput;
		this.mininumSize = minimumSize;
		this.acceptedMediaTypes = acceptedMediaTypes;
		this.ignoredMediaTypes = ignoredMediaTypes;
	}

	/**
	 * Returns the list of default encoded media types. This can be overriden by subclasses.
	 * By default, all media types are encoded (except those explicitely ignored).
	 * @return The list of default encoded media types.
	 */
	public static List<MediaType> getDefaultAcceptedMediaTypes()
	{
		List<MediaType> result = new ArrayList<MediaType>();
		result.add(MediaTypes.ALL);
		return result;
	}

	/**
	 * Returns the list of default ignored media types. This can be overriden by subclasses.
	 * By default, all archive, audio, image and video media types are ignored.
	 * @return The list of default ignored media types.
	 */
	public static List<MediaType> getDefaultIgnoredMediaTypes()
	{
		List<MediaType> result = new ArrayList<MediaType>();
		result.add(MediaTypes.APPLICATION_CABINET);
		result.add(MediaTypes.APPLICATION_GNU_ZIP);
		result.add(MediaTypes.APPLICATION_ZIP);
		result.add(MediaTypes.APPLICATION_GNU_TAR);
		result.add(MediaTypes.APPLICATION_JAVA_ARCHIVE);
		result.add(MediaTypes.APPLICATION_STUFFIT);
		result.add(MediaTypes.APPLICATION_TAR);
		result.add(MediaTypes.AUDIO_ALL);
		result.add(MediaTypes.IMAGE_ALL);
		result.add(MediaTypes.VIDEO_ALL);
		return result;
	}
	
   /**
    * Handles a call.
    * @param call The call to handle.
    */
	public void handle(Call call)
	{
		// Check if encoding of the call input is needed
		if(isEncodeInput() && canEncode(call.getInput()))
		{
			call.setInput(encode(call, call.getInput()));
		}
		
		// Delegate the handling to the attached Restlet
		super.handle(call);
		
		// Check if encoding of the call output is needed
		if(isEncodeOutput() && canEncode(call.getOutput()))
		{
			call.setOutput(encode(call, call.getOutput()));
		}
	}

	/**
	 * Indicates if a representation can be encoded.
	 * @param representation The representation to test.
	 * @return True if the call can be encoded.
	 */
	public boolean canEncode(Representation representation)
	{
		// Test the existance of the representation and that no existing encoding applies
		boolean result = ((representation != null) && (representation.getMetadata().getEncoding() == null)) ||
							  ((representation != null) && representation.getMetadata().getEncoding().equals(Encodings.IDENTITY));
		
		if(result)
		{
			// Test the size of the representation
			result = (getMinimumSize() == ALWAYS_ENCODE) || 
						(representation.getSize() == Representation.UNKNOWN_SIZE) ||
						(representation.getSize() >= getMinimumSize());
		}
		
		if(result)
		{
			// Test the acceptance of the media type
			MediaType mediaType = representation.getMetadata().getMediaType();
			boolean accepted = false;
			for(Iterator<MediaType> iter = getAcceptedMediaTypes().iterator(); !accepted && iter.hasNext(); )
			{
				accepted = iter.next().includes(mediaType);
			}
			
			result = accepted; 
		}
		
		if(result)
		{
			// Test the rejection of the media type
			MediaType mediaType = representation.getMetadata().getMediaType();
			boolean rejected = false;
			for(Iterator<MediaType> iter = getIgnoredMediaTypes().iterator(); !rejected && iter.hasNext(); )
			{
				rejected = iter.next().includes(mediaType);
			}

			result = !rejected;
		}

		return result;
	}

	/**
	 * Encodes a given representation if an encoding is supported by the client.
	 * @param call The parent call. 
	 * @param representation The representation to encode.
	 * @return The encoded representation or the original one if no encoding supported by the client.
	 */
	public Representation encode(Call call, Representation representation)
	{
		Representation result = representation;
		Encoding bestEncoding = getBestEncoding(call);
		
		if(bestEncoding != null)
		{
			result = new EncoderRepresentation(bestEncoding, representation);
		}
				
		return result;
	}
	
	/**
	 * Returns the best supported encoding for a given call.
	 * @param call The call to test.
	 * @return The best supported encoding for the given call.
	 */
	public Encoding getBestEncoding(Call call)
	{
		Encoding bestEncoding = null;
		Encoding currentEncoding = null;
		EncodingPref currentPref = null;
		float bestScore = 0F;
		
		for(Iterator<Encoding> iter = EncoderRepresentation.getSupportedEncodings().iterator(); iter.hasNext(); )
		{
			currentEncoding = iter.next();
			
			for(Iterator<EncodingPref> iter2 = call.getPreference().getEncodings().iterator(); iter2.hasNext(); )
			{
				currentPref = iter2.next();
				
				if(currentPref.getEncoding().equals(Encodings.ALL) || 
					currentPref.getEncoding().equals(currentEncoding))
				{
					// A match was found, compute its score
					if(currentPref.getQuality() > bestScore)
					{
						bestScore = currentPref.getQuality();
						bestEncoding = currentEncoding;
					}
				}
			}
		}
		
		return bestEncoding;
	}
	
	/**
	 * Indicates if the input representation should be encoded.
	 * @return True if the input representation should be encoded. 
	 */
	public boolean isEncodeInput()
	{
		return this.encodeInput;
	}

	/**
	 * Indicates if the input representation should be encoded.
	 * @param encodeInput True if the input representation should be encoded. 
	 */
	public void setEncodeInput(boolean encodeInput)
	{
		this.encodeInput = encodeInput;
	}

	/**
	 * Indicates if the output representation should be encoded.
	 * @return True if the output representation should be encoded. 
	 */
	public boolean isEncodeOutput()
	{
		return this.encodeOutput;
	}

	/**
	 * Indicates if the output representation should be encoded.
	 * @param encodeOutput True if the output representation should be encoded. 
	 */
	public void setEncodeOutput(boolean encodeOutput)
	{
		this.encodeOutput = encodeOutput;
	}

	/**
	 * Returns the minimum size a representation must have before compression is done. 
	 * @return The minimum size a representation must have before compression is done.
	 */
	public long getMinimumSize()
	{
		return mininumSize;
	}

	/**
	 * Sets the minimum size a representation must have before compression is done. 
	 * @param mininumSize The minimum size a representation must have before compression is done.
	 */
	public void setMinimumSize(long mininumSize)
	{
		this.mininumSize = mininumSize;
	}

	/**
	 * Returns the media types that should be encoded. 
	 * @return The media types that should be encoded.
	 */
	public List<MediaType> getAcceptedMediaTypes()
	{
		return this.acceptedMediaTypes;
	}

	/**
	 * Returns the media types that should be ignored. 
	 * @return The media types that should be ignored.
	 */
	public List<MediaType> getIgnoredMediaTypes()
	{
		return this.ignoredMediaTypes;
	}

}
