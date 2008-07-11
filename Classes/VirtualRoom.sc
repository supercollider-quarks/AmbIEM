/*
	Filename: VirtualRoom.sc 
	created: 21.4.2005 

	Copyright (C) IEM 2005, Christopher Frauenberger [frauenberger@iem.at] 

	This program is free software; you can redistribute it and/or 
	modify it under the terms of the GNU General Public License 
	as published by the Free Software Foundation; either version 2 
	of the License, or (at your option) any later version. 

	This program is distributed in the hope that it will be useful, 
	but WITHOUT ANY WARRANTY; without even the implied warranty of 
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the 
	GNU General Public License for more details. 

	You should have received a copy of the GNU General Public License 
	along with this program; if not, write to the Free Software 
	Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA. 

	IEM - Institute of Electronic Music and Acoustics, Graz 
	Inffeldgasse 10/3, 8010 Graz, Austria 
	http://iem.at
*/


/* 	NOTE: 
	the coordinate system is given according to the listener's head:
	x-axis (nose), y-axis (left-ear) and z-axis (vertex)
	
	* left-deep-corner			  *
		       x				  |
		       |	       		  |
		      / \		  		  depth
		y<---| z |			  |
		     ----				  |
							  |
	*---------- width ------------* (origin) 

	Rooms are defined by the origin and width/depth/height
*/


/* 	Class: VirtualEnvironment
	Provides a virtual room with sources, listener and binaural rendering
*/
VirtualRoom {
	
	// the path to Kemar
	classvar <>kemarPath = "KemarHRTF/";
	
	// the node proxies
	var <>encoded, <>bin, <>out, <>revIn;

	// maximum of sources allowed (exclusive reflections) 
	classvar <>maxSources = 10;
	
	// roomProperties (reverberation, reflections)
	var <>roomProperties;
		
	// the room as [x, y, z, depth, width, height] where xyz is the origin
	var <>roomSize;
	
	// the listener as NodeProxy.kr [ x, y, z, orientation] 
	var <>listener;
	
	// the list of sources for each instance
	var <>sources;
		
	/*	Class method: *new
		create an instance and initialise the instance's list of sources
	*/
	*new {
		^super.new.sources_(Dictionary.new)
			.roomProperties_(Dictionary.new);

	}

	/*	Method: init
		init the binaural rendering engine
	*/
	init {

		// initialise the rendering
		BinAmbi3O.kemarPath = kemarPath;
		// Note: different schemes may be passed to the bin NodeProxy, see source / help
		BinAmbi3O.init('1_4_7_4', doneAction: {
		 	// initialise the rendering chain when buffers are ready
		 	revIn = NodeProxy.audio(numChannels: 2);
			encoded = NodeProxy.audio(numChannels: 16);
			bin = NodeProxy.audio(numChannels: 2);
			bin.source = { BinAmbi3O.ar( encoded.ar ) };
			revIn.source = { bin.ar }; 	// not ideal...
			out = NodeProxy.audio(numChannels: 2);
			out.source = { arg m = 0.5, ro = 0.25, rG = 0.1, hD = 0.6;
					bin.ar + (FreeVerb2.ar( revIn.ar[0], revIn.ar[1], mix: m, room: ro, damp: hD) * rG)}; 
			listener = NodeProxy.control(numChannels: 4);
			listener.source = { |x=0, y=0, z=0, o=0| [ x, y, z, o] };
			"Virtual Room initialised".postln;
		});
	}

	// access methods	
	
	refGain_ { arg value;
		if(value.isNil) { roomProperties.removeAt(\refGain) } { roomProperties.put(\refGain, value) };
		sources.do({ |source| source.set(\refGain, value) });
	}
	refGain { ^roomProperties.at(\refGain).value ?? 0.6 }
	revGain_ { arg value;
		if(value.isNil) { roomProperties.removeAt(\revGain) } { roomProperties.put(\revGain, value) };
		out.set(\rG, value);
	}
	revGain { ^roomProperties.at(\revGain).value ?? 0.1 }
	// for compatibility - effects mix of FreeVerb (10sec revTime is the wettest it gets...)
	revTime_ { arg value;
		if(value.isNil) { roomProperties.removeAt(\revTime) } { roomProperties.put(\revTime, value) };
		out.set(\m, value/5);
	}
	revTime { ^roomProperties.at(\revTime).value ?? 1 }
	roomMix_ { arg value;
		if(value.isNil) { roomProperties.removeAt(\roomMix) } { roomProperties.put(\roomMix, value) };
		out.set(\m, value);
	}
	roomMix { ^roomProperties.at(\roomMix).value ?? 1 }
	hfDamping_ { arg value;
		if(value.isNil) { roomProperties.removeAt(\hfDamping) } { roomProperties.put(\hfDamping, value) };
		out.set(\hD, value);
	}
	hfDamping { ^roomProperties.at(\hfDamping).value ?? 0.6 }
	room_ { arg value;
		var diag;
		if(value.isNil || (value.size!=6)) { 
			"WARNING AmbIEM: using default room (0,0,0,5,8,5)".postln; 
			roomSize=[0, 0, 0, 5, 8, 5] } 
		{ roomSize = value };
		diag = hypot(roomSize[2]-roomSize[5], hypot(roomSize[0]-roomSize[3], roomSize[1]-roomSize[4]));
		out.set(\room, diag.linlin(0, 50, 0, 1)); // set the room size for FreeVerb
	}
	room { ^roomSize }
	

	/* 	Method: gui
		provide a GUI for the room properties
	*/
	gui {
		var w, f, s, v, t;
		var height = 15;
		s = Array.newClear(4);
		v = Array.newClear(4);
		roomProperties.put(\rTSpec, [0, 3].asSpec);
		roomProperties.put(\rMSpec, [0, 1].asSpec);			w = SCWindow("Virtual Room Properties", Rect(128, 64, 340, 150));
		w.view.decorator = f = FlowLayout(w.view.bounds,Point(4,4),Point(4,2));

		t = SCStaticText(w, Rect(0, 0, 75, height+2));
		t.string = "RefGain: ";
		v[0] = SCStaticText(w, Rect(0, 0, 30, height+2));
		s[0] = SCSlider(w, Rect(0, 0, 182, height));
		s[0].value = this.refGain;
		s[0].action = { 
			this.refGain = s[0].value;
			v[0].string = s[0].value.round(0.01).asString;
		};
		f.nextLine;
		
		t = SCStaticText(w, Rect(0, 0, 75, height+2));
		t.string = "RevGain: ";
		v[1] = SCStaticText(w, Rect(0, 0, 30, height+2));
		s[1] = SCSlider(w, Rect(0, 0, 182, height));
		s[1].value = this.revGain;
		s[1].action = { 
			this.revGain = s[1].value;
			v[1].string = s[1].value.round(0.01).asString;
		};
		f.nextLine;
		
		t = SCStaticText(w, Rect(0, 0, 75, height+2));
		t.string = "RoomMix: ";
		v[2] = SCStaticText(w, Rect(0, 0, 30, height+2));
		s[2] = SCSlider(w, Rect(0, 0, 182, height));
		s[2].value = this.roomMix;
		s[2].action = { 
			var val = s[2].value;
			this.roomMix = val;
			v[2].string = val.round(0.01).asString;
		};
		f.nextLine;
		
		t = SCStaticText(w, Rect(0, 0, 75, height+2));
		t.string = "hfDamping: ";
		v[3] = SCStaticText(w, Rect(0, 0, 30, height+2));
		s[3] = SCSlider(w, Rect(0, 0, 182, height));
		s[3].value = this.hfDamping;
		s[3].action = { 
			this.hfDamping = s[3].value;
			v[3].string = s[3].value.round(0.01).asString;
		};
		f.nextLine;
		s.do({|x|x.action.value });
		
		w.front;
	}
		
	/* 	Method: update
		rebuilds the encoded sources from the sources list
	*/
	update {	
		if (sources.size != 0, 
			// insert all sources and sum them
			{ encoded.source = sources.sum; }, 
			{ encoded.source = nil });
		// make sure refGain is set properly - not necessary??
		// csources.do({ |source| source.set(\refGain, VirtualRoom.refGain) });
	}
	
	/* 	method: addSourceLight
		add a source to the virtual room, but just use 4 reflections. This is though for using many sources 
		Parameter:
			key A string to identify the source
			source A mono sound source as NodeProxy.audio
			pos an Array with [xpos, ypos, zpos]
	*/
	addSourceLight { arg source, key, x, y, z;
	
		var  synthFunc; 
		
		("VirtualRoom: adding source " ++ key ++ " - the light way...").postln;
		
		synthFunc = { arg refGain = 0, xpos = x, ypos = y, zpos = z;
			var sourcePositions;
			var distances, gains, phis, thetas, delTimes, gainSource;
			var lx, ly, lz, lo;

			lx = listener.kr(1,0); ly = listener.kr(1,1); lz = listener.kr(1,2); lo = listener.kr(1,3);
			
			// direct source + 4 reflections		
			sourcePositions = [
				xpos, ypos, zpos, 
				this.room[0] + (2 * this.room[3]) - xpos, ypos, zpos, 
				this.room[0] - xpos, ypos, zpos, 
				xpos, this.room[1] + (2 * this.room[4]) - ypos, zpos, 
				xpos, this.room[1] -ypos, zpos
			];
			#phis, thetas, distances = sourcePositions.clump(3).collect({ |pos|
				var planeDist;
				planeDist = hypot(pos[1]-ly, pos[0]-lx);
				[atan2(ly-pos[1], pos[0]-lx) + lo, atan2(pos[2]-lz, planeDist), hypot(planeDist, lz - pos[2])];
			}).flop;		

			delTimes = ( distances / 340 );
			gains = (distances + 1).reciprocal; 
			(1..4).do({ | i | gains[i] = gains[i] * refGain });
		
			// sum up the encoded channels of all sources (original + reflections) 
			// DelayL replacement with BufRead....
			DelayL.ar( source.ar, 2, delTimes, gains.squared).collect( { |ch, i| 
				PanAmbi3O.ar(ch, phis[i], thetas[i]); }).sum;
		};

		// produce the NodeProxy.audio
		sources.put(key, NodeProxy.audio(numChannels: 16));
		sources[key].set(\refGain, this.refGain);
		sources[key].source = synthFunc; 
		
		// update the encoding node proxy
		this.update;
	}


	/* 	method: addSource
		add a source to the virtual room 
		Parameter:
			key A string to identify the source
			source A mono sound source as NodeProxy.audio
			pos A NodeProxy.control with [xpos, ypos, zpos]
	*/
	addSource { arg source, key, x, y, z;
	
		var  synthFunc; 
		
		("VirtualRoom: adding source " ++ key).postln;
		
		synthFunc = { arg refGain = 0, xpos = x, ypos = y, zpos = z;
			var sourcePositions;
			var distances, gains, phis, thetas, delTimes, gainSource;
			var phi, theta, planeDist, roomDist, refGain2; 
			var roomModel, sourceAndRefs; 
			var lx, ly, lz, lo;
			refGain2 =  refGain.squared;

			lx = listener.kr(1,0); ly = listener.kr(1,1); lz = listener.kr(1,2); lo = listener.kr(1,3);
										
			// calculate angles for source
			planeDist = hypot(ypos-ly, xpos-lx);
			phi = atan2(ly-ypos, xpos-lx) + lo;
			theta = atan2(zpos-lz, planeDist);
			roomDist = hypot(planeDist, lz - zpos);
			// calculate the room model
			roomModel = Room3D.new;
			roomModel.room = this.room;
			sourceAndRefs = [phi, theta, roomDist] ++ 
				roomModel.refs10polar(xpos, ypos, zpos, lx, ly, lz);
		
			#phis, thetas, distances = sourceAndRefs.clump(3).flop;
			delTimes = ( distances / 340 );
			gains = (distances + 1).reciprocal; 
			(1..10).do({ | i | gains[i] = gains[i] * if (i.inclusivelyBetween(5,8), refGain, refGain2) });
		
			// sum up the encoded channels of all sources (original + reflections) 
			// DelayL replacement with BufRead....
			DelayL.ar( source.ar, 2, delTimes, gains.squared).collect( { |ch, i| 
				PanAmbi3O.ar(ch, phis[i], thetas[i]); }).sum;
		};

		// produce the NodeProxy.audio
		sources.put(key, NodeProxy.audio(numChannels: 16));
		sources[key].set(\refGain, this.refGain);
		sources[key].source = synthFunc; 
		
		// update the encoding node proxy
		this.update;
	}
	
	
	/* 	method: removeSource
		remove a source to the virtual room 
		Parameter:
			key The key to identify the source
	*/
	removeSource { arg key;
	
		// free the NodeProxy and remove it from the instance dict
		sources[key].free; sources.removeAt(key);
		
		// update the encoding node proxy
		this.update;
	}
	
	/* 	method: free
		frees all resources 
	*/
	free {
		// free all sources
		sources.do(_.free);
		
		// free all NodeProxies of the rendering engine
		out.free;
		bin.free;
		encoded.free;
		revIn.free;
		
		// free the listener NodeProxy
		listener.free;
	}
	
}	

