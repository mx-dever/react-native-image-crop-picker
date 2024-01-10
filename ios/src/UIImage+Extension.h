//
//  UIImage+Extension.h
//  Pods
//
//  Created by Ivan Pusic on 09/05/2020.
//

#ifndef UIImage_Extension_h
#define UIImage_Extension_h

#import <UIKit/UIKit.h>

@interface UIImage (fixOrientation)

- (UIImage *)fixOrientation;
- (UIImage *)drawTimeWaterMaker:(NSString *)locationInfo;

@end

#endif /* UIImage_Extension_h */
