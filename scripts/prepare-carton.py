"""Rectify the six user-provided panels; does not infer packaging/contamination."""
import argparse,json,hashlib
from pathlib import Path
import cv2,numpy as np
p=argparse.ArgumentParser();p.add_argument('source');p.add_argument('output');args=p.parse_args()
spec=[
 ('front','앞면','IMG_1556.jpg',[[375,299],[866,288],[904,1565],[357,1566]],320,820),
 ('back','뒷면·부착 빨대','IMG_1554.jpg',[[253,315],[775,316],[835,1585],[270,1640]],320,820),
 ('ingredients','원재료 면','IMG_1555.jpg',[[454,272],[871,276],[904,1650],[444,1668]],256,820),
 ('nutrition','분리배출 표시 면','IMG_1557.jpg',[[458,310],[842,314],[866,1650],[422,1656]],256,820),
 ('top','윗면','IMG_1558.jpg',[[192,693],[1075,698],[1080,1179],[164,1187]],320,200),
 ('bottom','아랫면','IMG_1559.jpg',[[319,588],[1193,590],[1186,1281],[319,1270]],320,256),
]
out=Path(args.output);out.mkdir(parents=True,exist_ok=True);entries=[]
for ident,label,name,xy,width,height in spec:
 src=Path(args.source)/name;img=cv2.imread(str(src));assert img is not None
 points=np.array(xy,np.float32)*np.array([img.shape[1]/1368,img.shape[0]/1824],np.float32)
 target=np.array([[0,0],[width-1,0],[width-1,height-1],[0,height-1]],np.float32)
 panel=cv2.warpPerspective(img,cv2.getPerspectiveTransform(points,target),(width,height))
 cv2.imwrite(str(out/(ident+'.jpg')),panel,[cv2.IMWRITE_JPEG_QUALITY,92])
 entries.append({'id':ident,'label':label,'source':name,'sourceSha256':hashlib.sha256(src.read_bytes()).hexdigest(),'sourceSize':[img.shape[1],img.shape[0]],'sourceQuad':points.tolist(),'size':[width,height]})
(out/'manifest.json').write_text(json.dumps({'product':'매일두유 고단백 검은콩 190mL','scope':'one registered packaging design; not a waste classifier','source':'six photos supplied by user, 2026-09-07','syntheticEvaluationIsNotIndependent':True,'faces':entries},ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
print(json.dumps({'faces':len(entries),'output':str(out)}))
