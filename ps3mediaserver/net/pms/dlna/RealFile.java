/*
 * PS3 Media Server, for streaming any medias to your PS3.
 * Copyright (C) 2008  A.Brochard
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package net.pms.dlna;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

import net.pms.PMS;
import net.pms.dlna.virtual.TranscodeVirtualFolder;
import net.pms.dlna.virtual.VirtualFolder;
import net.pms.formats.Format;

public class RealFile extends DLNAResource {
	
	private String driveName;
	
	@Override
	public boolean isValid() {
		checktype();
		if (file.exists() && file.getName().length() > 4) {
			File ifo = new File(file.getParentFile(), file.getName().substring(0, file.getName().length()-4) + ".IFO");
			if (ifo.exists()) {
				ifoFileURI = file.getName().substring(0, file.getName().length()-4) + ".IFO";
				PMS.info("Found IFO file: " + ifoFileURI);
			}
			File srt = new File(file.getParentFile(), file.getName().substring(0, file.getName().length()-4) + ".srt");
			srtFile = srt.exists();
			if (!srtFile) {
				srt = new File(file.getParentFile(), file.getName().substring(0, file.getName().length()-4) + ".sub");
				srtFile = srt.exists();
			}
		}
		return file.exists() && (ext != null || file.isDirectory());
	}

	@Override
	public void discoverChildren() {
		super.discoverChildren();
		File files [] = file.listFiles();
		Arrays.sort(files);
		for(File f:files) {
			if (f.isDirectory())
				manageFile(f);
		}
		for(File f:files) {
			if (f.isFile())
				manageFile(f);
		}
	}
	
	private void manageFile(File f) {
		if ((f.isFile() || f.isDirectory()) && !f.isHidden()) {
			if (f.getName().toLowerCase().endsWith(".zip") || f.getName().toLowerCase().endsWith(".cbr") || f.getName().toLowerCase().endsWith(".jar")) {
				addChild(new ZippedFile(f));
			} else if (f.getName().toLowerCase().endsWith(".rar")) {
				addChild(new RarredFile(f));
			} else if (f.getName().toLowerCase().endsWith(".iso") || (f.isDirectory() && f.getName().toUpperCase().equals("VIDEO_TS"))) {
				addChild(new DVDISOFile(f));
			} else
				addChild(new RealFile(f));
		}
	}
	
	@Override
	public boolean refreshChildren() {
		File files [] = file.listFiles();
		ArrayList<File> addedFiles = new ArrayList<File>();
		ArrayList<DLNAResource> removedFiles = new ArrayList<DLNAResource>();
		int i = 0;
		for(File f:files) {
			if (!f.isHidden()) {
				boolean present = false;
				for(DLNAResource d:children) {
					if (i == 0 && !(d instanceof VirtualFolder))
						removedFiles.add(d);
					boolean addcheck = d instanceof RealFile;
					if (d.getName().equals(f.getName()) && (!addcheck || (addcheck && ((RealFile) d).lastmodified == f.lastModified()))) {
						removedFiles.remove(d);
						present = true;
					}
				}
				if (!present && (f.isDirectory() || PMS.get().getAssociatedExtension(f.getName()) != null)
						 && !f.getName().toLowerCase().endsWith(".iso") && !f.getName().toUpperCase().equals("VIDEO_TS"))
					addedFiles.add(f);
			}
			i++;
		}
		
		TranscodeVirtualFolder vf = null;
		for(DLNAResource r:children) {
			if (r instanceof TranscodeVirtualFolder) {
				vf = (TranscodeVirtualFolder) r;
				break;
			}
		}
		
		for(DLNAResource f:removedFiles) {
			children.remove(f);
			if (vf != null)
				for(int j=vf.children.size()-1;j>=0;j--) {
					if (vf.children.get(j).getName().equals(f.getName()));
						vf.children.remove(j);
				}
		}
		for(File f:addedFiles) {
			manageFile(f);
		}
		return removedFiles.size() != 0 || addedFiles.size() != 0;
	}

	private long lastmodified;
	private File file;
	
	public RealFile(File file) {
		this.file = file;
		lastmodified = file.lastModified();
	}

	public InputStream getInputStream() {
		try {
			return new FileInputStream(file);
		} catch (FileNotFoundException e) {
		}
		return null;
	}

	public long length() {
		if (player != null)
			return DLNAMediaInfo.TRANS_SIZE;
		else if (media != null && media.mediaparsed)
			return media.size;
		return file.length();
	}

	public String getName() {
		String name = null;
		if (file.getName().trim().equals("")) {
			if (PMS.get().isWindows()) {
				if (driveName == null) {
					driveName = PMS.get().getRegistry().getDiskLabel(file);
				}
			} 
			if (driveName != null && driveName.length() > 0)
				name = file.getAbsolutePath().substring(0, 1) + ":\\ [" + driveName + "]";
			else
				name = file.getAbsolutePath().substring(0, 1);
		}
		else
			name = file.getName();

		return name;
	}

	public boolean isFolder() {
		return file.isDirectory();
	}

	public File getFile() {
		return file;
	}

	public long lastModified() {
		return 0;
	}

	@Override
	public String getSystemName() {
		return file.getAbsolutePath();
	}

	@Override
	public void resolve() {
		if (file.isFile() && file.exists()) {
			if (media == null)
				media = new DLNAMediaInfo();
			media.parse(file, getType());
		}
		super.resolve();
	}

	@Override
	public String getThumbnailContentType() {
		return super.getThumbnailContentType();
	}

	@Override
	public InputStream getThumbnailInputStream() throws IOException {
		File folderThumb = new File(file.getParentFile(), file.getName() + ".cover.jpg");
		if (folderThumb.exists())
			return new FileInputStream(folderThumb);
		folderThumb = new File(file.getParentFile(), file.getName() + ".cover.png");
		if (folderThumb.exists())
			return new FileInputStream(folderThumb);
		else if (media != null && media.thumb != null)
			return media.getThumbnailInputStream();
		else return super.getThumbnailInputStream();
	}

	@Override
	protected String getThumbnailURL() {
		if (getType() == Format.IMAGE) // no thumbnail support for now for real based disk images
			return null;
		StringBuffer sb = new StringBuffer();
		sb.append(PMS.get().getServer().getURL());
		sb.append("/");
		if (media != null && media.thumb != null)
			return super.getThumbnailURL();
		else if (getType() == Format.AUDIO) {
			return null;
		}
		return super.getThumbnailURL();
	}

}
